package it.polimi.deepse.a3droid.utility;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import it.polimi.deepse.a3droid.bus.alljoyn.AlljoynBus;

/**
 * Created by danilo on 28/11/16.
 */

public class SessionSuffix {

    private static final String UNIQUE_SUFFIX_PATTERN = "\\.G[A-Za-z0-9]+";
    private static final Pattern suffixPattern = Pattern.compile(UNIQUE_SUFFIX_PATTERN);

    public static String removeServicePrefix(String nameWithPrefix){
        return nameWithPrefix.replace(AlljoynBus.SERVICE_PATH + ".", "");
    }

    public static String removeUniqueSuffix(String nameWithSuffix){
        return nameWithSuffix.replaceFirst(UNIQUE_SUFFIX_PATTERN, "");
    }

    public static String getUniqueSuffix(String nameWithSuffix){
        Matcher m = suffixPattern.matcher(nameWithSuffix);
        return m.group();
    }
}
