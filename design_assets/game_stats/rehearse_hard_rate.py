#!/usr/bin/env python3
"""Rules-only rehearsal: this is explicitly NOT human timing or an anti-cheat proof."""
from itertools import permutations
from statistics import mean

codes=[''.join(p) for p in permutations('0123456789',3)]
def feedback(secret,guess):
    exact=sum(a==b for a,b in zip(secret,guess))
    return exact,len(set(secret)&set(guess))-exact
counts=[]
for secret in codes:
    candidates=codes[:];count=0
    while True:
        guess=candidates[0];count+=1
        if guess==secret:break
        response=feedback(secret,guess)
        candidates=[c for c in candidates if feedback(c,guess)==response]
    counts.append(count)
print(f'All{len(codes)} valid secrets solved using only Hard R/W feedback.')
print(f'Mean guesses:{mean(counts):.3f}; min:{min(counts)}; max:{max(counts)}.')
print(f'Engineering fast-play assumption 1s/guess +2s restart: {3600/(mean(counts)+2):.1f} solves/hour.')
print('Draft ceiling600/hour allows6seconds/solve; actual human calibration required beforepublication.')
