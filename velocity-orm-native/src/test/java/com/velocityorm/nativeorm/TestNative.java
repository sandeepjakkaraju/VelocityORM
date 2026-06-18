package com.velocityorm.nativeorm;

public class TestNative {
    public static void main(String[] args) {
        System.out.println("Starting Native Test...");
        try {
            long hash = NativeRowIdentity.calculateRowHash("test_string");
            System.out.println("NATIVE HASH SUCCESS! Hash: " + hash);
            
            NativeCRUDOptimizer.markClean(123L, hash);
            boolean isDirty = NativeCRUDOptimizer.isDirty(123L, hash + 1);
            System.out.println("NATIVE DIRTY CHECK SUCCESS! Is Dirty? " + isDirty);
            
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
