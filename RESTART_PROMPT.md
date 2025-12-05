# Prompt for AI Developer

I want to recreate a Python project based on the following detailed specification and file structure.
Please implement the code exactly as described below.

## 1. Project Structure
Please strictly follow this file organization:

```text
ROOT/
    SPECIFICATION.md
    requirements.txt
    SPECIFICATION_RU.md
    README.md
    main.py
    cache/
        nbp/
    .github/
        workflows/
    src/
        fifo.py
        report_pdf.py
        nbp.py
        __init__.py
        parser.py
        utils.py
        processing.py
```

## 2. Dependencies (requirements.txt)
```text
requests
reportlab
pytest
pytest-mock
flake8
black
pre-commit
```

## 3. Detailed Specification (Business Logic)

# Спецификация проекта: IBKR Tax Assistant (Польша/PIT-38)

### Функционал

1.  **Обработка данных:**
    * Слияние отчетов (старый/новый счет).
    * Игнорирование технических трансферов (ACATS), если они не влияют на баланс.

2.  **Продвинутый Парсинг:**
    * **Санкции/Эскроу:** Обработка событий `Tendered` (конвертация ADR в локальные акции).
    * **Слияния/Спин-оффы:** Умный поиск тикера получаемой компании внутри описания (WBD, FG).

3.  **Финансы:**
    * **FIFO:** Строгая очередь.
    * **Курсы NBP:** Правило D-1, кэширование.

4.  **Отчетность (PDF):**
    * **Визуализация:** "Зебра" для читаемости. **Красная заливка** для заблокированных (RUB) активов.
    * **Структура:**
        * Портфель (с пометкой `*` для санкционных бумаг).
        * Журнал корпоративных действий.
        * Дивиденды (по месяцам + детализация).
        * **Помощник PIT-38**.

5.  **Качество:**
    * Тесты (`pytest`), Линтеры, CI/CD.

---
**Task:**
Please implement the core Python files (`src/*.py` and `main.py`) following the logic above. Ensure that:
1. FIFO logic handles negative inventory via Transfers.
2. Parser handles 'Tendered', 'Spin-off' (with hidden tickers), and Mergers.
3. PDF report has the 'Spacious' layout (1 month per page) and highlights Restricted Assets (Red background).
