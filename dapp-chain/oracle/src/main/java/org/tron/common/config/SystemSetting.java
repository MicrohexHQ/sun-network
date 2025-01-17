package org.tron.common.config;

public class SystemSetting {

  public static final long TRX = 1_000_000;

  public static final long FEE_LIMIT = 100 * TRX;

  public static final long RETRY_PROCESSING_INTERVAL = 10 * 60L;

  public static final int CREATE_POOL_SIZE = 100;

  public static final int BROADCAST_POOL_SIZE = 100;

  public static final int CHECK_POOL_SIZE = 100;

  public static final int CLIENT_MAX_RETRY = 5;

  public static final int CLIENT_RETRY_INTERVAL = 500;

}
