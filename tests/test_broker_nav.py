from src.broker_nav import find_broker_nav

STATEMENT = (
    'Statement,Data,Period,"January 1, 2026 - {end}"\n'
    "Account Information,Data,Base Currency,USD\n"
    "Net Asset Value,Header,Asset Class,Prior Total,Current Long,Current Short,Current Total,Change\n"
    "Net Asset Value,Data,Cash ,1,2,0,2,1\n"
    "Net Asset Value,Data,Stock,1,{stock},0,{stock},1\n"
    "Net Asset Value,Data,Total,1,{total},0,{total},1\n"
)


def write(path, end, stock, total):
    path.write_text(STATEMENT.format(end=end, stock=stock, total=total))


def test_picks_latest_statement_of_year(tmp_path):
    write(tmp_path / "a.csv", "September 30, 2026", "50000.5", "52000")
    write(tmp_path / "b.csv", "October 1, 2026", "57090.04", "58646.81")
    nav = find_broker_nav(tmp_path, 2026)
    assert nav == {
        "currency": "USD",
        "as_of": "2026-10-01",
        "stock_value": 57090.04,
        "net_asset_value": 58646.81,
        "source_file": "b.csv",
    }


def test_none_without_matching_statement(tmp_path):
    (tmp_path / "x.csv").write_text("Trades,Header,A\n")
    write(tmp_path / "a.csv", "October 1, 2026", "1", "2")
    assert find_broker_nav(tmp_path, 2025) is None
    assert find_broker_nav(tmp_path / "missing", 2026) is None
