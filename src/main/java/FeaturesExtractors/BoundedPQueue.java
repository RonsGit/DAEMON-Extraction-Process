package FeaturesExtractors;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.locks.ReentrantLock;

public class BoundedPQueue<E extends Comparable<Pair<String,Double>>> {
    /**
     * Lock used for all public operations
     */
    private final ReentrantLock lock;
    private PriorityBlockingQueue<Pair<String,Double>> queue ;
    private int size;

    ArrayList<Pair<String, Double>> getSorted(){
        ArrayList<Pair<String, Double>> sorted = new ArrayList<>(queue);
        sorted.sort((Comparator.reverseOrder()));
        return sorted;
    }

    void addStrings(ConcurrentMap<String, Double> toAddTo, int perGroup){
        int addedCounter = 0;
        ArrayList<Pair<String, Double>> sorted = new ArrayList<>(queue);
        sorted.sort((Comparator.reverseOrder()));
        for(Pair<String, Double> queueEntry: sorted){
            if(addedCounter<perGroup && !toAddTo.containsKey(queueEntry.getKey())) {
                System.out.println("Added String: " + queueEntry.getKey() + " With Score: " + queueEntry.getValue()
                + " To The Strings Collection Of Previous Combinations Computed");
                toAddTo.put(queueEntry.getKey(), queueEntry.getValue());
                addedCounter++;
            }
        }
    }

    HashSet<String> getKeys(){
        HashSet<String> toReturn = new HashSet<>();
        for(Pair<String, Double> p : queue)
            toReturn.add(p.getKey());
        return toReturn;
    }

    BoundedPQueue(int capacity){
        queue = new PriorityBlockingQueue<>(capacity, new CustomComparator<>());
        size = capacity;
        this.lock = new ReentrantLock();

    }

    boolean offer(Pair<String, Double> e) {
        final ReentrantLock lock = this.lock;
        lock.lock();
        Pair<String, Double> vl;
        if(queue.size()>= size)  {
            vl= queue.poll();
            assert vl != null;
            if(vl.compareTo(e)>=0)
                e=vl;
        }
        try {
            return queue.offer(e);
        } finally {
            lock.unlock();
        }

    }

    public Pair<String, Double> poll()  {
        return queue.poll();
    }

    int size() {
        return queue.size();
    }

    public static class CustomComparator<E extends Comparable<E>> implements Comparator<E> {

        @Override
        public int compare(E o1, E o2) {
            //give me a max heap
            return o2.compareTo(o1) *-1;
        }
    }

}