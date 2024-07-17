package com.nicweiss.editor.utils;

import java.security.SecureRandom;
import java.util.Base64;

public class Uuid {
    public static String generate(){
            SecureRandom random = new SecureRandom();
            Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();

            byte[] buffer = new byte[10];
            random.nextBytes(buffer);
            String newUUID = encoder.encodeToString(buffer);

            return newUUID;
    }
}
