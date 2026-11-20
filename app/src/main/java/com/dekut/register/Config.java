package com.dekut.register;

public final class Config {
    private Config() {}

    /** URL printed by `firebase deploy --only functions` for the stkPush function. */
    public static final String STK_PUSH_URL =
            "https://engine-sector-concert-valves.trycloudflare.com/stkPush";
}
