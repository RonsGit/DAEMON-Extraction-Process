package FeaturesExtractors;

public class Pair<FIRST extends Comparable<FIRST>, SECOND extends Comparable<SECOND>> implements Comparable<Pair<FIRST, SECOND>> {

    private final FIRST first;
    private final SECOND second;

    Pair(FIRST first, SECOND second) {
        this.first = first;
        this.second = second;
    }

    public static <FIRST extends Comparable<FIRST>, SECOND extends Comparable<SECOND>>
    Pair<FIRST, SECOND> of(FIRST first, SECOND second) {
        return new Pair<>(first, second);
    }

    @SuppressWarnings("NullableProblems")
    @Override
    public int compareTo(Pair<FIRST, SECOND> o) {
        return compare(second, o.second);
    }


    private <T extends Comparable<T>> int compare(T o1, T o2) {
        if(o1 == null) {
            if(o2 == null) {
                return 0;
            } else {
                return +1;
            }
        } else if(o2 == null) {
            return -1;
        } else {
            return o2.compareTo(o1)*-1;
        }
    }

    @Override
    public int hashCode() {
        return 31 * hashcode(first) + hashcode(second);
    }

    // todo move this to a helper class.
    private static int hashcode(Object o) {
        return o == null ? 0 : o.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Pair)) {
            return false;
        }
        //noinspection SimplifiableIfStatement
        if (this == obj) {
            return true;
        }
        return equal(first, ((Pair) obj).first)
                && equal(second, ((Pair) obj).second);
    }

    // todo move this to a helper class.
    private boolean equal(Object o1, Object o2) {
        return o1 == null ? o2 == null : (o1 == o2 || o1.equals(o2));
    }

    @Override
    public String toString() {
        return "(" + first + ", " + second + ')';
    }

    FIRST getKey() {
        return first;
    }

    SECOND getValue() {
        return second;
    }
}