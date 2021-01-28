-keep,includedescriptorclasses class org.bitcoinj.wallet.Protos$** { *; }
-keep,includedescriptorclasses class org.bitcoin.protocols.payments.Protos$** { *; }

-dontwarn org.bitcoinj.store.WindowsMMapHack
-dontwarn org.bitcoinj.store.LevelDBBlockStore
-dontwarn org.bitcoinj.store.LevelDBFullPrunedBlockStore**

-dontnote org.bitcoinj.crypto.DRMWorkaround
-dontnote org.bitcoinj.crypto.TrustStoreLoader$DefaultTrustStoreLoader
-dontnote com.subgraph.orchid.crypto.PRNGFixes

#Guava - proguard
-dontwarn com.google.common.base.Equivalence
-dontwarn java.lang.ClassValue

-keep class sun.misc.Unsafe { *; }