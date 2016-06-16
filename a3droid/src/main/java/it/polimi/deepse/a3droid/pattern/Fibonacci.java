package it.polimi.deepse.a3droid.pattern;

public class Fibonacci {
    public static int fib(int n) {
        n = n + 1;
        return (n < 2) ? n : fib(n - 1) + fib(n - 2);
    }
}
