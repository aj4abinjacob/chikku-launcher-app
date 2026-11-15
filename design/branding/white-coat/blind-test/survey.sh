#!/bin/zsh
# usage: survey.sh <round dir>
T=${0:A:h}
R=$1
$T/ask.sh $R 1 "You are Priya, 34, a school teacher who uses her Android phone for WhatsApp, YouTube and banking; not techy." &
$T/ask.sh $R 2 "You are Marcus, 19, a college student who likes customizing his phone and tries lots of apps." &
$T/ask.sh $R 3 "You are Helen, 62, a retired accountant who recently got her first smartphone." &
$T/ask.sh $R 4 "You are Diego, 41, a product designer who notices visual details in icons." &
$T/ask.sh $R 5 "You are Aisha, 27, a nurse who mostly uses her phone for messaging, maps and photos." &
wait
for f in $R/answer-*.txt; do echo "=== $f:t"; cat $f; echo; done
