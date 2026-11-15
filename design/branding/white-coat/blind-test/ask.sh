#!/bin/zsh
# usage: ask.sh <round dir> <persona id> <persona text>
cd "$1" || exit 1
P="$3"
print -r -- "$P

You just came across a new app icon while browsing the app store. You know NOTHING else about it: no name, no description. Use the Read tool to look at exactly two images in the current directory and nothing else: ./icon.png (the icon, large) and ./icon-small.png (the same icon at real size on a phone wallpaper). Do not read or list any other file.

Answer honestly, as yourself, in this exact format and nothing else:
TOP GUESS: <in a few words, what kind of app you think this is>
OTHER GUESSES: <two other possibilities>
WHAT I SEE: <one sentence describing the picture>
CONFIDENCE: <1-5>
WHY: <one sentence>" | claude -p --model sonnet --allowedTools=Read --disallowedTools=Bash,Write,Edit,WebFetch,WebSearch,Glob,Grep,Agent > "answer-$2.txt" 2>&1
