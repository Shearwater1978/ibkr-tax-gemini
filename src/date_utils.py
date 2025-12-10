# src/date_utils.py

from datetime import datetime, date
from typing import Optional

def calculate_holding_period_days(start_date: str, end_date: str) -> Optional[int]:
    """
    Calculates the holding period in days between two dates.
    Dates are expected in 'YYYY-MM-DD' format. Returns None if dates are invalid.

    Args:
        start_date: The purchase or acquisition date string.
        end_date: The sale or event date string.

    Returns:
        The number of days in the holding period, or None.
    """
    try:
        # Assuming the date format from the database is 'YYYY-MM-DD'
        date_format = '%Y-%m-%d'
        
        d1 = datetime.strptime(start_date, date_format).date()
        d2 = datetime.strptime(end_date, date_format).date()
        
        # We add 1 day to be inclusive of both start and end dates
        return (d2 - d1).days + 1
        
    except ValueError:
        # Handle cases where the date string format is incorrect
        return None

def determine_holding_category(holding_days: Optional[int]) -> str:
    """
    Categorizes the holding period into Short-Term or Long-Term.
    Uses a 365 days threshold for categorization (Long-Term >= 366 days).

    Args:
        holding_days: The calculated number of days the asset was held.

    Returns:
        A string category: 'Long-Term' (>= 366 days), 'Short-Term' (< 366 days), or 'N/A'.
    """
    if holding_days is None:
        return 'N/A'
    
    # Long-Term if held for more than a year (365 days)
    if holding_days >= 366:
        return 'Long-Term'
    else:
        return 'Short-Term'