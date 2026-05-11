package com.uiptv.util;

import com.uiptv.model.Account;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XtremeParserCoverageTest {

    @Test
    void parseAndSave_parsesLabeledAndUnlabeledBlocks_andSkipsInvalidOnes() {
        List<Account> saved = new ArrayList<>();
        Function<String, Account> accountProvider = name -> null;
        Consumer<Account> accountSaver = saved::add;

        String text = """
                http://alpha.example:8080 userA passA

                https://beta.example:9443 Username: userB Password: passB

                this block has no url
                userC passC

                http://gamma.example:8080 User: lonelyUser
                """;

        List<Account> parsed = new XtremeParser(accountProvider, accountSaver).parseAndSave(text, false, false);

        assertEquals(2, parsed.size());
        assertEquals(2, saved.size());
        assertEquals("userA", parsed.get(0).getUsername());
        assertEquals("passA", parsed.get(0).getPassword());
        assertEquals("userB", parsed.get(1).getUsername());
        assertEquals("passB", parsed.get(1).getPassword());
        assertTrue(parsed.stream().allMatch(account -> account.getType() == AccountType.XTREME_API));
    }

    @Test
    void parseAndSave_completesMissingCredentialFromUnlabeledTokens_andStripsSingleCharacterNoise() {
        List<Account> saved = new ArrayList<>();
        Function<String, Account> accountProvider = name -> null;
        Consumer<Account> accountSaver = saved::add;

        String text = """
                http://delta.example:8080 user=deltaUser deltaPass x

                http://epsilon.example:8080 epsilonUser pass=epsilonPass y

                http://zeta.example:8080 \u0007 user=zetaUser pw=zetaPass
                """;

        List<Account> parsed = new XtremeParser(accountProvider, accountSaver).parseAndSave(text, false, false);

        assertEquals(3, parsed.size());
        assertEquals(3, saved.size());
        assertEquals("deltaUser", parsed.get(0).getUsername());
        assertEquals("deltaPass", parsed.get(0).getPassword());
        assertEquals("epsilonUser", parsed.get(1).getUsername());
        assertEquals("epsilonPass", parsed.get(1).getPassword());
        assertEquals("zetaUser", parsed.get(2).getUsername());
        assertEquals("zetaPass", parsed.get(2).getPassword());
    }
}
