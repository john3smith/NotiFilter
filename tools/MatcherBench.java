package com.local.noti_filter;
import java.util.*;
public final class MatcherBench {
    static volatile int sink;
    public static void main(String[] args) {
        for (int count : new int[]{64, 256, 512}) {
            List<Rule> rules = new ArrayList<>();
            for (int i=0;i<count;i++) rules.add(new Rule(i,"","","missing"+i+",today;other"+i,true));
            for(int i=0;i<2000;i++) sink += RuleMatcher.matches(rules,"app","ordinary notification")?1:0;
            double[] samples=new double[7];
            for(int s=0;s<samples.length;s++) {
                long start=System.nanoTime();
                for(int i=0;i<3000;i++) sink += RuleMatcher.matches(rules,"app","ordinary notification")?1:0;
                samples[s]=(System.nanoTime()-start)/3000.0/1000;
            }
            Arrays.sort(samples);
            System.out.printf(Locale.ROOT,"rules=%d median_us=%.3f min_us=%.3f max_us=%.3f%n",count,samples[3],samples[0],samples[6]);
        }
        if(sink!=0)throw new AssertionError("Unexpected match");
    }
}
