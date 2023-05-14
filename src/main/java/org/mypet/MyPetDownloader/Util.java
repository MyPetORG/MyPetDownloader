package org.mypet.MyPetDownloader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;

public class Util {


    public static String readUrlContent(String address) throws IOException {
        return readUrlContent(address, 2000);
    }

    public static String readUrlContent(String address, int timeout) throws IOException {
        StringBuilder contents = new StringBuilder(2048);
        BufferedReader br = null;

        try {
            URL url = new URL(address);
            HttpURLConnection huc = (HttpURLConnection) url.openConnection();
            huc.setConnectTimeout(timeout);
            huc.setReadTimeout(timeout);
            huc.setRequestMethod("GET");
            huc.connect();
            br = new BufferedReader(new InputStreamReader(huc.getInputStream()));
            String line;
            while ((line = br.readLine()) != null) {
                contents.append(line);
            }
        } finally {
            try {
                if (br != null) {
                    br.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return contents.toString();
    }

    public static int versionCompare(String str1, String str2) {
        String[] vals1 = str1.split("\\.");
        String[] vals2 = str2.split("\\.");
        if (vals1.length > vals2.length) {
            int oldLength = vals2.length;
            vals2 = Arrays.copyOf(vals2, vals1.length);
            for (int i = oldLength; i < vals1.length; i++) {
                vals2[i] = "0";
            }
        } else if (vals2.length > vals1.length) {
            int oldLength = vals1.length;
            vals1 = Arrays.copyOf(vals1, vals2.length);
            for (int i = oldLength; i < vals2.length; i++) {
                vals1[i] = "0";
            }
        }
        int i = 0;
        while (i < vals1.length - 1 && vals1[i].equals(vals2[i])) {
            i++;
        }
        if (i < vals1.length) {
            try {
                return Integer.valueOf(vals1[i]).compareTo(Integer.valueOf(vals2[i]));
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }
}
