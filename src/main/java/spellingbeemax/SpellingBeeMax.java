package spellingbeemax;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Determines which set of letters allows the highest possible total score in the NYT Spelling Bee.
 *
 * <p>The NYT Spelling Bee is a puzzle where players have to find the most words possible that are
 * made up of a set of letters. There are 7 letters every day, one of which is <i>required</i>. A
 * valid word consists entirely of those 7 letters, and must be at least 4 letters long. A letter
 * can appear in the word more than once or not at all, except that the required letter must appear
 * at least once.
 *
 * <p>The NYT has an unpublished list of words that are allowed as guesses, which necessarily is a
 * bit arbitrary. NENE is in, DEMOTIC is out. We can use as an approximation {@code
 * /usr/share/dict/words}, filtered to include only words that consist of four or more lowercase
 * letters. (So no short words, no proper nouns, no hyphens or apostrophes.) Allowed NYT words often
 * turn out not to be in this dictionary, and vice versa, so it all probably evens out.
 *
 * <p>Every Spelling Bee puzzle contains at least one <i>pangram</i>, which is a word that contains
 * each of the 7 letters at least once. Instead of iterating over all 26C7 (657,800) sets of 7
 * letters, we can use only those sets that allow a pangram. That reduces runtime from about 20
 * seconds to about 1 second. (Thanks to Jason Parker-Burlingham for this idea.)
 *
 * <p>A data structure that is often useful for this sort of problem is a bitmask with one bit per
 * word in the word list. We can have 26 of these to allow us to find all the words that contain any
 * given letter. Given a set of 7 letters, we can just OR the bitsets for the 19 other letters in
 * order to eliminate words that don’t consist of just those 7 letters. Then we can count the
 * remaining words, and we can use further Boolean operations to find the 7 subsets for the 7
 * possible required letters.
 *
 * <p>We'll report the set of 7 letters that allows the largest number of possible words, and the
 * set that allows the highest total score. In both cases we'll compute those separately for each
 * one of the 7 possible required letters. With the word list I used, there are two possibilities
 * for the set with the <i>lowest</i> score while still having a pangram: BEJKOUX and AHNPRXY, both
 * with required X, having total score 14 in both cases. (The solutions are left as an exercise for
 * the reader.)
 */
public class SpellingBeeMax {
  public static void main(String[] args) throws Exception {
    long start = System.nanoTime();
    List<String> wordList = readWordList(DICTIONARY);
    System.out.printf("word list size %d\n", wordList.size());
    var x = new SpellingBeeMax(wordList);
    x.solve();
    long stop = System.nanoTime();
    System.out.printf("Elapsed time %.1fs\n", (stop - start) / 1e9);
  }

  /**
   * Reads the word list, applying filters to retain only words that would probably be valid
   * Spelling Bee words.
   */
  private static List<String> readWordList(String dictionary) throws IOException {
    try (Stream<String> stream = Files.lines(Path.of(dictionary))) {
      // As an optimization we omit words with more than 7 distinct letters, since those can never
      // be a valid word regardless of the letter set.
      return stream
          .filter(w -> ALLOWED.matcher(w).matches())
          .filter(w -> LetterSet.fromWord(w).cardinality() <= 7)
          .collect(collectingAndThen(toList(), Collections::unmodifiableList));
    }
  }

  private static final String DICTIONARY = "/usr/share/dict/words";

  /**
   * The dictionary contains lots of proper nouns and adjectives, and words with apostrophes, so
   * exclude those, as well as words with fewer than 4 letters.
   */
  private static final Pattern ALLOWED = Pattern.compile("[a-z]{4,}");

  /** The word list. */
  private final List<String> wordList;

  /**
   * The score of each word in the word list, so {@code wordList.get(i)} has score {@code
   * scores.get(i)}.
   */
  private final List<Integer> scores;

  /**
   * 26 sets of words, corresponding to all the words that have an A in them, a B, etc. For example
   * {@code letterToWords.get('q' - 'a')} is the set of all words that have a Q in them.
   */
  private final List<WordSet> letterToWords;

  private SpellingBeeMax(List<String> wordList) {
    this.wordList = wordList;
    this.scores =
        IntStream.range(0, wordList.size())
            .map(i -> score(wordList.get(i)))
            .boxed()
            .collect(collectingAndThen(toList(), Collections::unmodifiableList));
    this.letterToWords =
        IntStream.range(0, 26)
            .mapToObj(unused -> new WordSet())
            .collect(toCollection(ArrayList::new));
    for (int i = 0; i < wordList.size(); i++) {
      String word = wordList.get(i);
      for (char c : word.toCharArray()) {
        wordsContaining(c).add(i);
      }
    }
  }

  /**
   * Run through each possible letter sets that allows at least one pangram, and compute which sets
   * have the best or worst scores, and result in the most or least possible words.
   */
  private void solve() {
    LetterSetPlusRequired bestLetterSet = null;
    WordSet bestLetterSetWords = new WordSet();
    LetterSetPlusRequired highestLetterSet = null;
    WordSet highestLetterSetWords = new WordSet();
    LetterSetPlusRequired worstLetterSet = null;
    WordSet worstLetterSetWords = allWords();
    LetterSetPlusRequired lowestLetterSet = null;
    WordSet lowestLetterSetWords = allWords();
    Set<LetterSet> pangramSets =
        wordList.stream()
            .map(LetterSet::fromWord)
            .filter(set -> set.cardinality() == 7)
            .filter(
                // Jason Parker-Burlingham observes that the Spelling Bee letter sets seem to
                // observe these unwritten rules: never S, and never both E and R.
                set -> !set.contains('s') && !(set.contains('e') && set.contains('r')))
            .collect(toCollection(LinkedHashSet::new));
    System.out.printf("%d sets of letters allow at least one pangram\n", pangramSets.size());
    for (LetterSet letterSet : pangramSets) {
      WordSet candidates = wordsContainingOnly(letterSet);
      for (char required : letterSet) {
        LetterSetPlusRequired newLetterSet = new LetterSetPlusRequired(letterSet, required);
        WordSet candidatesWithRequired = candidates.clone();
        candidatesWithRequired.retainAll(wordsContaining(required));
        if (candidatesWithRequired.cardinality() > bestLetterSetWords.cardinality()) {
          System.out.printf("%s gets more words than %s\n", newLetterSet, bestLetterSet);
          bestLetterSet = newLetterSet;
          bestLetterSetWords = candidatesWithRequired;
        }
        if (candidatesWithRequired.cardinality() < worstLetterSetWords.cardinality()
            || (candidatesWithRequired.cardinality() == worstLetterSetWords.cardinality()
                && candidatesWithRequired.score() < worstLetterSetWords.score())) {
          System.out.printf(
              "%s gets fewer words or a worse score than %s\n", newLetterSet, worstLetterSet);
          worstLetterSet = newLetterSet;
          worstLetterSetWords = candidatesWithRequired;
        }
        int score = candidatesWithRequired.score();
        if (score > highestLetterSetWords.score()) {
          System.out.printf(
              "%s scores %d, which beats %s scoring %d\n",
              newLetterSet, score, highestLetterSet, highestLetterSetWords.score());
          highestLetterSet = newLetterSet;
          highestLetterSetWords = candidatesWithRequired;
        }
        if (score <= lowestLetterSetWords.score()) {
          System.out.printf(
              "%s scores %d, which is no better than %s\n", letterSet, score, lowestLetterSet);
          lowestLetterSet = newLetterSet;
          lowestLetterSetWords = candidatesWithRequired;
        }
      }
    }
    System.out.printf(
        "Best letter set is %s, which has %s scoring %d in total\n",
        bestLetterSet, bestLetterSetWords.wordCount(), bestLetterSetWords.score());
    System.out.printf("Words for that set: %s\n", bestLetterSetWords);
    System.out.printf(
        "Highest-scoring letter set is %s, which has %s scoring %d in total\n",
        highestLetterSet, highestLetterSetWords.wordCount(), highestLetterSetWords.score());
    System.out.printf("Words for that set: %s\n", highestLetterSetWords);
    System.out.printf(
        "Worst letter set is %s, which has %s scoring %d in total\n",
        worstLetterSet, worstLetterSetWords.wordCount(), worstLetterSetWords.score());
    System.out.printf("Words for that set: %s\n", worstLetterSetWords);
    System.out.printf(
        "Lowest-scoring letter set is %s, which has %s scoring %d in total\n",
        lowestLetterSet, lowestLetterSetWords.wordCount(), lowestLetterSetWords.score());
    System.out.printf("Words for that set: %s\n", lowestLetterSetWords);
  }

  /** The integer code of the given lowercase ASCII letter, with {@code 'a'} being 0, etc. */
  private static int ord(char c) {
    return c - 'a';
  }

  /** The character {@code c} such that {@code ord(c) == ord}. */
  private static char chr(int ord) {
    return (char) (ord + 'a');
  }

  /**
   * The uppercase version of the given ASCII letter. Nonsense result if {@code c} isn't a letter.
   *
   * <p>We retain the original lowercase spelling of the words in the word list, because they are
   * more readable that way, but when showing letters in a set we show them in uppercase.
   */
  private static char upper(char c) {
    return (char) (c & ~0x20);
  }

  /** The set of all words in the word list that contain {@code c}. */
  private WordSet wordsContaining(char c) {
    return letterToWords.get(ord(c));
  }

  /**
   * The score of the given word. The score for a four-letter word is 1. The score for a longer word
   * is its number of letters. A "pangram" is a word that uses all 7 letters, and that scores its
   * number of letters plus 7.
   */
  private static int score(String word) {
    if (word.length() == 4) {
      return 1;
    }
    if (isPangram(word)) {
      return word.length() + 7;
    }
    return word.length();
  }

  /**
   * True if the given word is a pangram. This is independent of the letter set that might have been
   * used to produce the word: if it has 7 distinct letters then it's a pangram.
   */
  private static boolean isPangram(String word) {
    // The >= 7 check is just an optimization.
    return word.length() >= 7 && LetterSet.fromWord(word).cardinality() == 7;
  }

  /**
   * Returns the set of words whose letters consist entirely of letters in the given set. To compute
   * this, we can construct the union of all words that contain a letter <i>not</i> in the set. The
   * result is the complement of that union. (Equivalently, we can start with the set of all words
   * and then remove the subset for each letter not in {@code letterSet}.)
   */
  private WordSet wordsContainingOnly(LetterSet letterSet) {
    WordSet candidates = allWords();
    for (char c : letterSet.complement()) {
      candidates.removeAll(wordsContaining(c));
      if (candidates.isEmpty()) {
        break;
      }
    }
    return candidates;
  }

  /** Returns a new {@code WordSet} that initially contains all the words in the word list. */
  WordSet allWords() {
    WordSet wordSet = new WordSet();
    wordSet.bitSet.set(0, wordList.size());
    return wordSet;
  }

  /**
   * A mutable set of words from the word list. This doesn't implement {@link Set} because that is a
   * bit annoying and wouldn't actually serve any purpose.
   */
  private class WordSet {
    private final BitSet bitSet = new BitSet(wordList.size());
    private int score = -1;

    WordSet() {}

    @Override
    protected WordSet clone() {
      WordSet clone = new WordSet();
      clone.bitSet.or(bitSet);
      return clone;
    }

    int cardinality() {
      return bitSet.cardinality();
    }

    /** A string representing the number of words. Just so we can avoid saying "1 words". */
    String wordCount() {
      int n = cardinality();
      return n + (n == 1 ? " word" : " words");
    }

    boolean isEmpty() {
      return bitSet.isEmpty();
    }

    void add(int index) {
      bitSet.set(index);
      modified();
    }

    void removeAll(WordSet that) {
      bitSet.andNot(that.bitSet);
      modified();
    }

    void retainAll(WordSet that) {
      bitSet.and(that.bitSet);
      modified();
    }

    List<String> asList() {
      return bitSet.stream().mapToObj(wordList::get).collect(toCollection(ArrayList::new));
    }

    int score() {
      if (score < 0) {
        score = bitSet.stream().map(scores::get).sum();
      }
      return score;
    }

    void modified() {
      score = -1;
    }

    /**
     * {@inheritDoc}
     *
     * <p>The returned string consists of each word from the set, followed by its score and by
     * {@code *} if it is a pangram.
     */
    @Override
    public String toString() {
      return asList().stream()
          .map(
              word ->
                  word
                      + "("
                      + SpellingBeeMax.score(word)
                      + ")"
                      + ((LetterSet.fromWord(word).cardinality() == 7) ? "*" : ""))
          .collect(joining(", "));
    }
  }

  /** An immutable set of letters. */
  private static class LetterSet implements Iterable<Character> {
    /**
     * Bit i is set if {@code 'a' + i} is in the set. This obviously means we can't deal with an
     * alphabet that has more than 32 letters.
     */
    private final int letterBits;

    LetterSet() {
      this(0);
    }

    LetterSet(int letterBits) {
      this.letterBits = letterBits;
    }

    static LetterSet fromWord(String word) {
      int letterBits = 0;
      for (int i = 0; i < word.length(); i++) {
        letterBits |= (1 << ord(word.charAt(i)));
      }
      return new LetterSet(letterBits);
    }

    LetterSet complement() {
      return new LetterSet(~letterBits & ((1 << 26) - 1));
    }

    LetterSet minus(char c) {
      return new LetterSet(letterBits & ~(1 << ord(c)));
    }

    boolean contains(char c) {
      return (letterBits & (1 << ord(c))) != 0;
    }

    int cardinality() {
      return Integer.bitCount(letterBits);
    }

    @Override
    public String toString() {
      return StreamSupport.stream(this.spliterator(), false)
          .map(c -> String.valueOf(upper(c)))
          .collect(joining(""));
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof LetterSet && ((LetterSet) obj).letterBits == letterBits;
    }

    @Override
    public int hashCode() {
      return letterBits;
    }

    @Override
    public Iterator<Character> iterator() {
      return new LetterSetIterator(letterBits);
    }

    private static class LetterSetIterator implements Iterator<Character> {
      /**
       * The letters we haven't yet returned from the iterator, using the same bit representation as
       * in {@link LetterSet}.
       */
      private int letterBits;

      LetterSetIterator(int letterBits) {
        this.letterBits = letterBits;
      }

      @Override
      public boolean hasNext() {
        return letterBits != 0;
      }

      @Override
      public Character next() {
        if (letterBits == 0) {
          throw new NoSuchElementException();
        }
        int ord = Integer.numberOfTrailingZeros(letterBits);
        letterBits &= ~(1 << ord);
        return chr(ord);
      }
    }
  }

  /** An immutable set of letters, one of which is "required". */
  private static class LetterSetPlusRequired {
    private final LetterSet letterSet;
    private final char required;

    LetterSetPlusRequired(LetterSet letterSet, char required) {
      if (!letterSet.contains(required)) {
        throw new IllegalArgumentException(letterSet + " does not contain " + required);
      }
      this.letterSet = letterSet;
      this.required = required;
    }

    @Override
    public String toString() {
      LetterSet others = letterSet.minus(required);
      return "[" + upper(required) + "]" + others;
    }
  }
}
