package com.uiptv.util;

import com.uiptv.model.Account;
import com.uiptv.service.AccountService;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XtremeParserCoverageTest {

    @Test
    void parseAndSave_parsesLabeledAndUnlabeledBlocks_andSkipsInvalidOnes() {
        AccountService accountService = Mockito.mock(AccountService.class);
        List<Account> saved = new ArrayList<>();
        Mockito.doAnswer(invocation -> {
            saved.add(invocation.getArgument(0));
            return null;
        }).when(accountService).save(Mockito.any(Account.class));

        String text = """
                http://alpha.example:8080 userA passA

                https://beta.example:9443 Username: userB Password: passB

                this block has no url
                userC passC

                http://gamma.example:8080 User: lonelyUser
                """;

        try (MockedStatic<AccountService> accountServiceStatic = Mockito.mockStatic(AccountService.class)) {
            accountServiceStatic.when(AccountService::getInstance).thenReturn(accountService);

            List<Account> parsed = new XtremeParser().parseAndSave(text, false, false);

            assertEquals(2, parsed.size());
            assertEquals(2, saved.size());
            assertEquals("userA", parsed.get(0).getUsername());
            assertEquals("passA", parsed.get(0).getPassword());
            assertEquals("userB", parsed.get(1).getUsername());
            assertEquals("passB", parsed.get(1).getPassword());
            assertTrue(parsed.stream().allMatch(account -> account.getType() == AccountType.XTREME_API));
        }
    }

    @Test
    void parseAndSave_completesMissingCredentialFromUnlabeledTokens_andStripsSingleCharacterNoise() {
        AccountService accountService = Mockito.mock(AccountService.class);
        Mockito.doNothing().when(accountService).save(Mockito.any(Account.class));

        String text = """
                http://delta.example:8080 user=deltaUser deltaPass x

                http://epsilon.example:8080 epsilonUser pass=epsilonPass y

                http://zeta.example:8080 \u0007 user=zetaUser pw=zetaPass
                """;

        try (MockedStatic<AccountService> accountServiceStatic = Mockito.mockStatic(AccountService.class)) {
            accountServiceStatic.when(AccountService::getInstance).thenReturn(accountService);

            List<Account> parsed = new XtremeParser().parseAndSave(text, false, false);

            assertEquals(3, parsed.size());
            assertEquals("deltaUser", parsed.get(0).getUsername());
            assertEquals("deltaPass", parsed.get(0).getPassword());
            assertEquals("epsilonUser", parsed.get(1).getUsername());
            assertEquals("epsilonPass", parsed.get(1).getPassword());
            assertEquals("zetaUser", parsed.get(2).getUsername());
            assertEquals("zetaPass", parsed.get(2).getPassword());
        }
    }
}
