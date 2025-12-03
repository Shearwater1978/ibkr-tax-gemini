from decimal import Decimal
from src.parser import extract_ticker, classify_trade_type, parse_manual_history

def test_extract_ticker():
    assert extract_ticker("AGR(US05351W1036) Cash Dividend") == "AGR"
    assert extract_ticker("MSFT(US5949181045) Cash Dividend") == "MSFT"
    assert extract_ticker("TSLA") == "TSLA" 
    assert extract_ticker(None) == "UNKNOWN"

def test_classify_trade_type():
    assert classify_trade_type("Buy Order", Decimal("10")) == "BUY"
    assert classify_trade_type("Sell Order", Decimal("-5")) == "SELL"
    assert classify_trade_type("ACATS RECEIVE", Decimal("100")) == "TRANSFER"

def test_ignore_dummy_tickers(tmp_path):
    d = tmp_path / "dummy.csv"
    d.write_text("Date,Ticker,Quantity,Price,Currency,Commission\n2022-01-01,EXAMPLE,10,100,USD,-1")
    trades = parse_manual_history(str(d))
    assert len(trades) == 0

def test_valid_manual_history(tmp_path):
    d = tmp_path / "valid.csv"
    d.write_text("Date,Ticker,Quantity,Price,Currency,Commission\n2022-01-01,AAPL,10,150,USD,-1")
    trades = parse_manual_history(str(d))
    assert len(trades) == 1
    assert trades[0]['ticker'] == "AAPL"
