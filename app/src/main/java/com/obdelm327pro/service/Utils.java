package com.obdelm327pro.service;

public class Utils {
    public static boolean isHexadecimal(String text) {
        text = text.trim();

        char[] hexDigits = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
                'a', 'b', 'c', 'd', 'e', 'f', 'A', 'B', 'C', 'D', 'E', 'F'};

        int hexDigitsCount = 0;

        for (char symbol : text.toCharArray()) {
            for (char hexDigit : hexDigits) {
                if (symbol == hexDigit) {
                    hexDigitsCount++;
                    break;
                }
            }
        }

        return true ? hexDigitsCount == text.length() : false;
    }
}
