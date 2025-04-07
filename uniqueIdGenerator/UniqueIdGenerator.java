import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class UniqIdGenerator_Sacalable {
    public static void main(String[] args) throws ExecutionException, InterruptedException {
        TransactionIdGenerator transactionIdGenerator = new TransactionIdGenerator("PAY");

        String id1 = transactionIdGenerator.generate("UUID1").value();
        Thread.onSpinWait();
        String id2 = transactionIdGenerator.generate("UUID2").value();

        System.out.println(id1 + "\n"+ id2);

        ExecutorService executor = Executors.newFixedThreadPool(MAX_THREADS);
        List<Future<?>> futures = new ArrayList<>();

        for(int i=0;i<MAX_THREADS;i++){
            futures.add(executor.submit(()->testIdGeneration(transactionIdGenerator)));
        }
        for(Future<?> future : futures){
            future.get();
        }
        executor.shutdown();

        int totalGenerated = MAX_THREADS * MAX_IDS;
        System.out.println("\nTest Results:");
        System.out.println("Total IDs generated: " + totalGenerated);
        System.out.println("Unique IDs collected: " + ALL_IDS.size());
        System.out.println("Duplicates detected: " + DUPLICATE_COUNT.get());
        System.out.println(ALL_IDS);

        if (ALL_IDS.size() != totalGenerated) {
            throw new AssertionError("DUPLICATE IDS FOUND!");
        } else {
            System.out.println("✅ All IDs are unique!");
        }
    }

    private static final int MAX_THREADS = 10;
    private static final int MAX_IDS = 1000;
    private static final Set<String> ALL_IDS = ConcurrentHashMap.newKeySet();
    private static final AtomicInteger DUPLICATE_COUNT = new AtomicInteger(0);
    private static void testIdGeneration(TransactionIdGenerator idGenerator){
        for(int i=0;i<MAX_IDS ;i++){
            String id = idGenerator.generate().value();

            if(!ALL_IDS.add(id)){
                System.err.println("Duplicate ID: " + id);
                DUPLICATE_COUNT.incrementAndGet();
            }
            try {
                Thread.sleep(ThreadLocalRandom.current().nextInt(10));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

    }
}
