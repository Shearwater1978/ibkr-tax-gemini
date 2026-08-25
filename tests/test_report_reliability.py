import pandas as pd
import pytest

from src.diagnostics import ReportExportError
from src.excel_exporter import export_to_excel


def test_excel_export_failure_is_raised(monkeypatch, tmp_path):
    def fail_writer(*args, **kwargs):
        raise OSError("disk full")

    monkeypatch.setattr(pd, "ExcelWriter", fail_writer)

    with pytest.raises(ReportExportError):
        export_to_excel({}, str(tmp_path / "report.xlsx"), {}, {})
