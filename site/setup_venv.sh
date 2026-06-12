#!/usr/bin/env sh

if [ ! -d .venv ]; then
  echo "Creating virtual Python environment…"
  python3 -m venv .venv
fi

if [ ! -f .venv/bin/zensical ]; then
  echo "Installing zensical…"
  source .venv/bin/activate
  pip install -r requirements.txt
fi

echo "Done!"
echo "Activate virtual environment with \"source .venv/bin/activate\" or \"source .venv/bin/activate.fish\""
