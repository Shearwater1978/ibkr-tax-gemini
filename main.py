# main.py

import argparse
from datetime import date
from collections import defaultdict
import sys
import pandas as pd 

# Импорт модулей проекта
from src.data_collector import collect_all_trade_data
from src.excel_exporter import export_to_excel
from src.db_connector import DBConnector 
from src.processing import process_yearly_data 

# Попытка импорта генератора PDF
try:
    from src.report_pdf import generate_pdf
    PDF_AVAILABLE = True
except ImportError:
    print("ВНИМАНИЕ: src/report_pdf.py не найден. PDF экспорт отключен.")
    PDF_AVAILABLE = False

def prepare_data_for_pdf(target_year, raw_trades, realized_gains, dividends, inventory):
    """
    Адаптер: Конвертирует результаты обработки в структуру словаря,
    которую ожидает src/report_pdf.py.
    """
    
    # --- СПИСОК САНКЦИОННЫХ БУМАГ ---
    RESTRICTED_TICKERS = {
        "YNDX", "OZON", "VKCO", "FIVE", "FIXP", "HHR", "QIWI", "CIAN", "GEMC", "HMSG", "MDMG",
        "POLY", "PLZL", "GMKN", "NLMK", "CHMF", "MAGN", "RUAL", "ALRS", "PHOR", "GLTR",
        "GAZP", "LKOH", "NVTK", "ROSN", "TATN", "SNGS", "SNGSP",
        "SBER", "SBERP", "VTBR", "TCSG", "CBOM",
        "MTSS", "AFKS", "AFLT"
    }
    RESTRICTED_CURRENCIES = {"RUB"}

    # 1. Фильтрация сырых сделок для секции "История"
    history_trades = []
    corp_actions = []
    
    # Сортировка по ключу 'Date' (PascalCase из БД)
    raw_trades.sort(key=lambda x: x['Date'])
    
    for t in raw_trades:
        # Проверяем год. Ключ 'Date'
        if t['Date'].startswith(str(target_year)):
            event_type = t['EventType'] # Ключ 'EventType'
            
            # Разделяем события. В историю попадают только BUY и SELL.
            if event_type in ['SPLIT', 'TRANSFER', 'MERGER', 'SPINOFF']:
                corp_actions.append({
                    'date': t['Date'], 
                    'ticker': t['Ticker'], 
                    'type': event_type,
                    'qty': float(t['Quantity']) if t['Quantity'] else 0,
                    'ratio': 1, 
                    'source': t.get('Description', 'DB')
                })
            
            elif event_type in ['BUY', 'SELL']: # <--- СТРОГИЙ ФИЛЬТР
                history_trades.append({
                    'date': t['Date'], 
                    'ticker': t['Ticker'], 
                    'type': event_type,
                    'qty': float(t['Quantity']) if t['Quantity'] else 0,
                    'price': float(t['Price']) if t['Price'] else 0,
                    'commission': float(t['Fee']) if t['Fee'] else 0,
                    'currency': t['Currency']
                })
            # События DIVIDEND и TAX сюда НЕ попадают (они идут в dividends)

    # 2. Агрегация дивидендов по месяцам
    monthly_divs = defaultdict(lambda: {'gross_pln': 0.0, 'tax_pln': 0.0, 'net_pln': 0.0})
    formatted_divs = []
    
    for d in dividends:
        # dividends приходят из модуля processing.py, который обычно возвращает snake_case
        date_str = d['ex_date']
        month_key = date_str[5:7] # MM
        
        gross = d['gross_amount_pln']
        tax = d.get('tax_withheld_pln', 0.0)
        net = gross - tax
        
        monthly_divs[month_key]['gross_pln'] += gross
        monthly_divs[month_key]['tax_pln'] += tax
        monthly_divs[month_key]['net_pln'] += net
        
        formatted_divs.append({
            'date': date_str,
            'ticker': d['ticker'],
            'amount': d.get('gross_amount_pln', 0) / d.get('rate', 1) if d.get('rate') else 0,
            'currency': d.get('currency', 'UNK'),
            'rate': d.get('rate', 1.0),
            'amount_pln': gross,
            'tax_paid_pln': tax
        })

    # 3. Прирост капитала (Capital Gains)
    cap_gains_data = []
    for g in realized_gains:
        # Аналогично, realized_gains приходит из processing.py в snake_case
        cap_gains_data.append({
            'revenue_pln': g['sale_amount'],
            'cost_pln': g['cost_basis']
        })

    # 4. Активы на конец периода (Inventory)
    aggregated_holdings = defaultdict(float)
    restricted_status = {}

    for i in inventory:
        ticker = i['ticker']
        qty = i['quantity']
        aggregated_holdings[ticker] += qty
        
        if ticker in RESTRICTED_TICKERS or i.get('currency') in RESTRICTED_CURRENCIES:
            restricted_status[ticker] = True

    holdings_data = []
    for ticker, total_qty in aggregated_holdings.items():
        if abs(total_qty) > 0.000001:
            holdings_data.append({
                'ticker': ticker,
                'qty': total_qty,
                'is_restricted': restricted_status.get(ticker, False),
                'fifo_match': True 
            })
    
    holdings_data.sort(key=lambda x: x['ticker'])

    # 5. Диагностика
    per_curr = defaultdict(float)
    for d in dividends:
        per_curr[d.get('currency', 'UNK')] += d['gross_amount_pln']

    pdf_payload = {
        'year': target_year,
        'data': {
            'holdings': holdings_data,
            'trades_history': history_trades,
            'corp_actions': corp_actions,
            'monthly_dividends': dict(monthly_divs),
            'dividends': formatted_divs,
            'capital_gains': cap_gains_data,
            'per_currency': dict(per_curr),
            'diagnostics': {
                'tickers_count': len(aggregated_holdings),
                'div_rows_count': len(dividends),
                'tax_rows_count': 0 
            }
        }
    }
    return pdf_payload

def main():
    parser = argparse.ArgumentParser(description="Налоговый калькулятор IBKR")
    
    # Аргументы фильтрации
    parser.add_argument('--target-year', type=int, default=date.today().year, 
                        help='Год для расчета налогов (например, 2024).')
    parser.add_argument('--ticker', type=str, default=None, 
                        help='Фильтр по тикеру акции (например, AAPL).')
    
    # Аргументы экспорта
    parser.add_argument('--export-excel', action='store_true', help='Экспорт полной истории в Excel.')
    parser.add_argument('--export-pdf', action='store_true', help='Экспорт налогового отчета в PDF.')
    
    args = parser.parse_args()
    
    print(f"Запуск расчета налогов за {args.target_year} год...")
    
    # --- 1. Загрузка данных из БД ---
    raw_trades = []
    try:
        # Инициализируем соединение (переменные окружения подтянутся внутри)
        with DBConnector() as db:
            db.initialize_schema() 
            raw_trades = db.get_trades_for_calculation(target_year=args.target_year, ticker=args.ticker)
            print(f"ИНФО: Загружено {len(raw_trades)} записей из БД.")
    except Exception as e:
        # Вывод ошибки без sys.exit здесь, так как контекстный менеджер закроет соединение
        print(f"КРИТИЧЕСКАЯ ОШИБКА: Не удалось подключиться или получить данные. {e}")
        sys.exit(1)
        
    if not raw_trades:
        print("ВНИМАНИЕ: Сделки не найдены. Сначала импортируйте данные.")
        return

    # --- 2. Запуск логики FIFO ---
    print("ИНФО: Запуск сопоставления FIFO и конвертации по курсу ЦБ...")
    try:
        # process_yearly_data работает с оригинальными ключами БД (PascalCase + TradeId)
        realized_gains, dividends, inventory = process_yearly_data(raw_trades, args.target_year)
    except Exception as e:
        print(f"КРИТИЧЕСКАЯ ОШИБКА во время обработки (processing): {e}")
        sys.exit(1)
    
    # Расчет итогов
    total_pl = sum(r['profit_loss'] for r in realized_gains)
    total_dividends = sum(d['gross_amount_pln'] for d in dividends)
    
    print(f"\n--- Результаты за {args.target_year} ---")
    print(f"Реализованный P&L (FIFO): {total_pl:.2f} PLN")
    print(f"Дивиденды (Брутто): {total_dividends:.2f} PLN")
    print(f"Открытые позиции (лотов): {len(inventory)}")

    # Подготовка данных для экспорта
    file_name_suffix = f"_{args.ticker}" if args.ticker else ""

    # --- 3. Экспорт в Excel ---
    if args.export_excel:
        print("\nНачинаем экспорт в Excel...")
        try:
            sheets_dict, ticker_summary = collect_all_trade_data(realized_gains, dividends, inventory)
            
            summary_metrics = {
                "Total P&L": f"{total_pl:.2f} PLN", 
                "Total Dividends (Gross)": f"{total_dividends:.2f} PLN",
                "Report Year": args.target_year,
                "Filtered Ticker": args.ticker if args.ticker else "Все тикеры",
                "Database Records": len(raw_trades)
            }
            output_path_xlsx = f"output/tax_report_{args.target_year}{file_name_suffix}.xlsx"
            export_to_excel(sheets_dict, output_path_xlsx, summary_metrics, ticker_summary)
            print(f"УСПЕХ: Excel отчет сохранен в {output_path_xlsx}")
        except Exception as e:
            print(f"ОШИБКА при экспорте в Excel: {e}")

    # --- 4. Экспорт в PDF ---
    if args.export_pdf:
        if PDF_AVAILABLE:
            print("\nНачинаем экспорт в PDF...")
            output_path_pdf = f"output/tax_report_{args.target_year}{file_name_suffix}.pdf"
            
            # Подготовка данных для PDF с учетом ключей PascalCase
            try:
                pdf_data = prepare_data_for_pdf(args.target_year, raw_trades, realized_gains, dividends, inventory)
                generate_pdf(pdf_data, output_path_pdf)
                print(f"УСПЕХ: PDF отчет сохранен в {output_path_pdf}")
            except Exception as e:
                print(f"ОШИБКА: Не удалось создать PDF: {e}")
        else:
            print("ОШИБКА: Модуль генерации PDF (src/report_pdf.py) не найден.")

    print("Обработка завершена.")

if __name__ == "__main__":
    main()