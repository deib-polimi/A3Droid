package it.polimi.deepse.a3droid.utility;

public class Fibonacci {
    public static int fib(int n) {
        return (n < 2) ? n : fib(n - 1) + fib(n - 2);
    }
}
