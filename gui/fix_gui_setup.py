import os

def write_lines(path, lines):
    try:
        with open(path, 'w', encoding='utf-8') as f:
            for line in lines:
                f.write(line + "\n")
        print(f"✅ Successfully updated: {path}")
    except Exception as e:
        print(f"❌ Error writing {path}: {e}")

def main():
    # We are rewriting gui/package.json to include the "start" script.
    # We also ensure "main" points to "main.js".
    
    package_json_content = [
        "{",
        "  \"name\": \"ibkr-tax-gui\",",
        "  \"version\": \"1.0.0\",",
        "  \"description\": \"Electron UI for IBKR Tax Calculator\",",
        "  \"main\": \"main.js\",",
        "  \"scripts\": {",
        "    \"start\": \"electron .\"",
        "  },",
        "  \"author\": \"\",",
        "  \"license\": \"MIT\",",
        "  \"devDependencies\": {",
        "    \"electron\": \"^33.0.0\"",
        "  }",
        "}"
    ]

    # Ensure the gui directory exists
    if not os.path.exists("gui"):
        os.makedirs("gui")
        print("Created gui/ directory")

    write_lines("gui/package.json", package_json_content)

if __name__ == "__main__":
    main()
