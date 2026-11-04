package br.com.manager.utils;

import br.com.manager.models.dto.RosterPolicyDto;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class RosterPolicyKeys {

    private RosterPolicyKeys() {}

    public static String key(RosterPolicyDto policy) {
        var text = String.join(
            "\n",
            policy.country(),
            policy.season(),
            policy.reference(),
            policy.windowStart(),
            policy.windowEnd()
        );
        try {
            return (
                "policy-" +
                HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)))
                    .substring(0, 24)
            );
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }
}
