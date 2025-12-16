import os
import tokenize
from io import BytesIO
from langdetect import detect, LangDetectException
from deep_translator import GoogleTranslator

# Settings
TARGET_LANG = 'en'
translator = GoogleTranslator(source='auto', target=TARGET_LANG)

def is_english(text):
    """
    Checks if the text is already in English.
    """
    try:
        # If text is too short or mostly symbols, skip detection
        if len(text.strip()) < 3: 
            return True
        lang = detect(text)
        return lang == 'en'
    except LangDetectException:
        # If unable to detect language, assume it shouldn't be touched
        return True

def translate_text(text):
    """
    Translates text to the target language using Google Translate.
    """
    try:
        translated = translator.translate(text)
        return translated
    except Exception as e:
        print(f"Error translating '{text}': {e}")
        return text

def process_file(filepath):
    """
    Reads a Python file, finds non-English comments, translates them,
    and rewrites the file.
    """
    with open(filepath, 'rb') as f:
        source_bytes = f.read()

    try:
        # Use tokenize to safely identify comments vs strings
        tokens = list(tokenize.tokenize(BytesIO(source_bytes).readline))
    except tokenize.TokenError:
        print(f"Skipping {filepath}: Token error")
        return

    modified = False
    processed_tokens = []
    
    for token in tokens:
        token_type = token.type
        token_string = token.string
        
        if token_type == tokenize.COMMENT:
            # Remove the '#' symbol to get the raw text
            content = token_string.lstrip('#').strip()
            
            if content and not is_english(content):
                print(f"Translating in {filepath}: {content}")
                translated_content = translate_text(content)
                
                # Restore comment format (# + space + text)
                new_string = f"# {translated_content}"
                
                # Create a new token with the translated content
                # Tuples are immutable, so we convert to list, modify, then back to tuple
                token_list = list(token)
                token_list[1] = new_string
                token = tuple(token_list)
                modified = True
        
        processed_tokens.append(token)

    if modified:
        # untokenize reconstructs the code. 
        # Note: This might slightly alter spacing/indentation in some edge cases.
        new_source = tokenize.untokenize(processed_tokens).decode('utf-8')
        
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(new_source)
        print(f"Updated {filepath}")

def main():
    # Walk through the directory structure
    for root, dirs, files in os.walk("."):
        # Ignore hidden folders, venv, and cache
        if ".git" in root or "venv" in root or "__pycache__" in root:
            continue
            
        for file in files:
            # Process only .py files and exclude the script itself
            if file.endswith(".py") and file != "translate_comments.py":
                filepath = os.path.join(root, file)
                process_file(filepath)

if __name__ == "__main__":
    main()
