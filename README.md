This project computes the letter set that would produce the maximum score for the NYT's Spelling Bee
puzzle.

The NYT Spelling Bee is a puzzle where players have to find the most words possible that are made
up of a set of letters. There are 7 letters every day, one of which is *required*. A valid word
consists entirely of those 7 letters, and must be at least 4 letters long. A letter can appear in
the word more than once or not at all, except that the required letter must appear at least once.

The NYT has an unpublished list of words that are allowed as guesses, which necessarily is a bit
arbitrary. NENE is in, DEMOTIC is out. We can use as an approximation `/usr/share/dict/words`,
filtered to include only words that consist of four or more lowercase letters. (So no short words,
no proper nouns, no hyphens or apostrophes.) Allowed NYT words often turn out not to be in this
dictionary, and vice versa, so it all probably evens out.

This assumes a Unix-like system that actually *has* a `/usr/share/dict/words`. Furthermore, although
MacOS has one, it is very big and contains many borderline words, so the results there are less
plausible.

Every Spelling Bee puzzle contains at least one *pangram*, which is a word that contains each of the
7 letters at least once. Instead of iterating over all 26C7 (657,800) sets of 7 letters, we can use
only those sets that allow a pangram. That reduces runtime from about 20 seconds to about 1 second.
(Thanks to Jason Parker-Burlingham for this idea.)

Each word has a score, computed as follows: a 4-letter word scores 1 point, a word of more than 4
letters scores as many points as it has letters, and a pangram scores an additional 7 points.

We'll report the set of 7 letters that allows the largest number of possible words, and the set that
allows the highest total score. In both cases we'll compute those separately for each one of the 7
possible required letters.

We also take into account some unwritten rules, also due to Jason: the set never has an S, and it
never has both an E and an R.

With the word list I used, the highest-scoring set is [N]AEGILT, where the [N] indicates that
that is the required letter. 193 words for a total score of 1254. The set with the most words is
[E]ADILNT, which however scores slightly less: 206 words scoring 1165.

Of course the puzzle would be incredibly tedious with such a large number of possibilities. Lifting
the unwritten restrictions just mentioned would lead to even more possibilities: [E]AINRST has 505
words scoring 3408, while [E]ALPRST has 524 words scoring 3087.

There are two possibilities for the set with the *lowest* score while still having a pangram:
[X]BEJKOU and [X]AHNPRY. These both have exactly one possible word, which is a pangram. (Which words
those are is left as an exercise for the reader.)
