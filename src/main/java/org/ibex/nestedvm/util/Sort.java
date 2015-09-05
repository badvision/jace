package org.ibex.nestedvm.util;

public final class Sort {
    private Sort() { }

    public interface Comparable { public int compareTo(Object o); }
    public interface CompareFunc { public int compare(Object a, Object b); }

    private static final CompareFunc comparableCompareFunc = new CompareFunc() {
        public int compare(Object a,Object b) { return ((Comparable)a).compareTo(b); }
    };

    public static void sort(Comparable[] a) { sort(a,comparableCompareFunc); }
    public static void sort(Object[] a, CompareFunc c) { sort(a,c,0,a.length-1); }

    private static void sort(Object[] a, CompareFunc c, int start, int end) {
        Object tmp;
        if(start >= end) return;
        if(end-start <= 6) {
            for(int i=start+1;i<=end;i++) {
                tmp = a[i];
                int j;
                for(j=i-1;j>=start;j--) {
                    if(c.compare(a[j],tmp) <= 0) break;
                    a[j+1] = a[j];
                }
                a[j+1] = tmp;
            }
            return;
        }

        Object pivot = a[end];
        int lo = start - 1;
        int hi = end;

        do {
            while((lo < hi) && c.compare(a[++lo],pivot) < 0) { }
            while((hi > lo) && c.compare(a[--hi],pivot) > 0) { }
            tmp = a[lo]; a[lo] = a[hi]; a[hi] = tmp;
        } while(lo < hi);

        tmp = a[lo]; a[lo] = a[end]; a[end] = tmp;

        sort(a, c, start, lo-1);
        sort(a, c, lo+1, end);
    }
}
