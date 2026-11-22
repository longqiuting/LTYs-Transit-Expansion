package dev.js.mtrheadlight.net;

import dev.js.mtrheadlight.LteLog;

public final class ServerAuthority {
   private static volatile boolean dedicated;
   private static volatile boolean remoteAuthoritative;
   private static volatile boolean remoteDamage;
   private static volatile int remoteHighKmh;
   private static boolean warned;

   private ServerAuthority() {
   }

   public static void markDedicated() {
      dedicated = true;
   }

   public static boolean dedicated() {
      return dedicated;
   }

   public static boolean remoteAuthoritative() {
      return remoteAuthoritative;
   }

   public static void acceptRemote(LteChannel.ServerHello hello) {
      if (hello != null && hello.version == 1) {
         remoteDamage = hello.damage;
         remoteHighKmh = hello.highDamageMaxKmh;
         remoteAuthoritative = hello.damage;
      }
   }

   public static void clearRemote() {
      remoteAuthoritative = false;
      remoteDamage = false;
      remoteHighKmh = 0;
      warned = false;
   }

   public static boolean authoritative() {
      return dedicated || remoteAuthoritative;
   }

   public static boolean remoteDamageEnabled() {
      return remoteDamage;
   }

   public static void warnIfMismatch(boolean localDamage, int localHighKmh) {
      if (!warned && remoteAuthoritative) {
         warned = true;
         if (localDamage != remoteDamage) {
            LteLog.LOGGER.warn("[lte] server-side collision is authoritative: damage={} on the server, damage={} in this client's config - using the server's setting", remoteDamage, localDamage);
         } else if (localHighKmh != remoteHighKmh && remoteHighKmh > 0) {
            LteLog.LOGGER.warn("[lte] collision damage tier boundary differs: high={}km/h on the server, high={}km/h in this client's config - the server decides", remoteHighKmh, localHighKmh);
         }

      }
   }
}
