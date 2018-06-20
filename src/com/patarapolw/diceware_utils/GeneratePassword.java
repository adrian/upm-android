package com.patarapolw.diceware_utils;

/*
  Created by patarapolw on 5/5/18.
  Password Generator based on https://github.com/patarapolw/diceware_utils
  that is, a diceware-passphrase generator, but the length of the passphrase
  is truncated to 10 - 20 character, while inserting some symbols.
 */

import android.app.Activity;
import android.content.Context;

import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;

public class GeneratePassword extends Activity {
    SecureRandom random = new SecureRandom();
    Policy policy;
    Context myContext;

    public GeneratePassword(){}

    public GeneratePassword(Context context){
        policy = new Policy(context);
        myContext = context;
    }

    private String[] getAllKeywords () {
        String fileName = "eff-long.txt";

        byte[] buffer;
        try {
            InputStream is = myContext.getAssets().open(fileName);
            int size = is.available();
            buffer = new byte[size];
            is.read(buffer);
            is.close();
        }
        catch (IOException ex) {
            ex.printStackTrace();
            return null;
        }

        String allKeywords = new String(buffer);
        return allKeywords.split("\n");
    }

    public String[] keywordList () {
        int numberOfKeywords = 6;

        String[] allKeywords = getAllKeywords();
        String[] result = new String[numberOfKeywords];

        for(int i=0; i<result.length; i++){
            assert allKeywords != null;
            result[i] = allKeywords[random.nextInt(allKeywords.length)];
        }

        return result;
    }

    public String newPassword() {
        String[] keywordResource = keywordList();
        String password;

        keywordResource = title_case_all(keywordResource);
        keywordResource = policy.insert_symbol_one(keywordResource);
        password = toPassword(keywordResource);
        while (password.length() > policy.getLength_max()) {
            keywordResource = shorten_one(keywordResource);
            password = toPassword(keywordResource);
        }

        int timeout = 3000;  // In milliseconds
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeout){
            if(!policy.isConform(keywordResource)){
                keywordResource = policy.conformize(keywordResource);
            } else {
                password = toPassword(keywordResource);
                return password;
            }
        }

        return "";
    }

    private String toPassword(String[] keywordList){
        StringBuilder builder = new StringBuilder();

        for (String s: keywordList){
            builder.append(s);
        }

        return builder.toString();
    }

    private String[] shorten_one(String[] listOfKeywords){
        int maxLength = 3;
        int length = random.nextInt(maxLength - 1) + 1;
        int index = random.nextInt(listOfKeywords.length);
        for(int i=0; i<listOfKeywords.length; i++){
            if(i == index){
                if(listOfKeywords[i].length() >= length) {
                    listOfKeywords[i] = listOfKeywords[i].substring(0, length);
                }
            }
        }
        return listOfKeywords;
    }

    private String[] title_case_all(String[] listOfKeywords){
        String keyword;
        for(int i=0; i<listOfKeywords.length; i++){
            keyword = listOfKeywords[i];
            listOfKeywords[i] = Character.toUpperCase(keyword.charAt(0)) + keyword.substring(1);
        }
        return listOfKeywords;
    }
}
