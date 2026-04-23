package com.uiptv.service;

import com.uiptv.model.Account;
import com.uiptv.shared.BaseJson;

import java.util.ArrayList;
import java.util.List;

public class AccountResolver {
    public static final String PIN_SVG_STEM_PATH = "m 289.99122,309.99418 c -0.66028,0.58344 -50.08221,-43.19021 -52.50936,-45.29992 -2.42734,-2.10956 -51.06934,-43.57426 -52.83626,-46.26739 -1.76673,-2.69328 13.04928,-12.78624 13.70956,-13.36969 0.66024,-0.58341 12.52054,-14.06148 14.94736,-11.95215 2.42733,2.10957 37.03325,55.97684 38.80018,58.66996 1.76673,2.69328 38.54876,57.6358 37.88852,58.21919 z";
    public static final String PIN_SVG_HEAD_PATH = "m 56.34936,106.22036 c 20.30938,0.88278 45.68909,32.12704 73.173,75.95489 18.76942,29.93108 45.31357,11.58173 54.19751,2.7927 8.31501,-8.2259 25.42173,-32.179 -3.72915,-51.99008 -42.68539,-29.00919 -72.93354,-55.50764 -73.173,-75.954905 L 81.58356,81.621661 Z";
    public static final String PIN_SVG_STEM_FILL = "#cad2d2";
    public static final String PIN_SVG_HEAD_FILL = "#e30000";
    public static final String PIN_SVG_VIEW_BOX = "0 0 320 320";
    public static final double PIN_SVG_SCALE = 0.075;

    public List<AccountRow> resolveAccounts() {
        List<AccountRow> rows = new ArrayList<>();
        for (Account account : AccountService.getInstance().getAll().values()) {
            rows.add(fromAccount(account));
        }
        return rows;
    }

    public AccountRow fromAccount(Account account) {
        AccountRow row = new AccountRow();
        if (account == null) {
            return row;
        }
        row.setAccountName(account.getAccountName());
        row.setDbId(account.getDbId());
        row.setType(account.getType() != null ? account.getType().name() : "");
        row.setPinToTop(account.isPinToTop());
        row.setPinSvgStemPath(PIN_SVG_STEM_PATH);
        row.setPinSvgHeadPath(PIN_SVG_HEAD_PATH);
        row.setPinSvgStemFill(PIN_SVG_STEM_FILL);
        row.setPinSvgHeadFill(PIN_SVG_HEAD_FILL);
        row.setPinSvgViewBox(PIN_SVG_VIEW_BOX);
        row.setPinSvgScale(PIN_SVG_SCALE);
        return row;
    }

    public static class AccountRow extends BaseJson {
        private String accountName;
        private String dbId;
        private String type;
        private boolean pinToTop;
        private String pinSvgStemPath;
        private String pinSvgHeadPath;
        private String pinSvgStemFill;
        private String pinSvgHeadFill;
        private String pinSvgViewBox;
        private double pinSvgScale;

        public String getAccountName() {
            return accountName;
        }

        public void setAccountName(String accountName) {
            this.accountName = accountName;
        }

        public String getDbId() {
            return dbId;
        }

        public void setDbId(String dbId) {
            this.dbId = dbId;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public boolean isPinToTop() {
            return pinToTop;
        }

        public void setPinToTop(boolean pinToTop) {
            this.pinToTop = pinToTop;
        }

        public String getPinSvgStemPath() {
            return pinSvgStemPath;
        }

        public void setPinSvgStemPath(String pinSvgStemPath) {
            this.pinSvgStemPath = pinSvgStemPath;
        }

        public String getPinSvgHeadPath() {
            return pinSvgHeadPath;
        }

        public void setPinSvgHeadPath(String pinSvgHeadPath) {
            this.pinSvgHeadPath = pinSvgHeadPath;
        }

        public String getPinSvgStemFill() {
            return pinSvgStemFill;
        }

        public void setPinSvgStemFill(String pinSvgStemFill) {
            this.pinSvgStemFill = pinSvgStemFill;
        }

        public String getPinSvgHeadFill() {
            return pinSvgHeadFill;
        }

        public void setPinSvgHeadFill(String pinSvgHeadFill) {
            this.pinSvgHeadFill = pinSvgHeadFill;
        }

        public String getPinSvgViewBox() {
            return pinSvgViewBox;
        }

        public void setPinSvgViewBox(String pinSvgViewBox) {
            this.pinSvgViewBox = pinSvgViewBox;
        }

        public double getPinSvgScale() {
            return pinSvgScale;
        }

        public void setPinSvgScale(double pinSvgScale) {
            this.pinSvgScale = pinSvgScale;
        }
    }
}
