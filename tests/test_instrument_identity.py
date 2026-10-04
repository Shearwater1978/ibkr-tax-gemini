from src.instrument_identity import resolve_placeholder_ticker


def test_placeholder_ticker_resolves_to_real_symbol():
    description = (
        "OKE(US6826801036) CUSIP/ISIN Change to (US30609A1097) "
        "(2682320D, ONEOK INC, US30609A1097)"
    )
    assert resolve_placeholder_ticker("2682320D", description) == "OKE"
    assert resolve_placeholder_ticker("KO", description) == "KO"
    assert resolve_placeholder_ticker("2682320D", "") == "2682320D"
