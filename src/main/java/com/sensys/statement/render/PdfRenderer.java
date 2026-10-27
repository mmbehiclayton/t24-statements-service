package com.sensys.statement.render;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Component;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.ColumnText;
import com.sensys.statement.StatementProperties;
import com.sensys.statement.statement.CustomerStatement;
import com.sensys.statement.statement.Statement;

@Component
public class PdfRenderer {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final Color HEAD_BG = new Color(0x1F, 0x3A, 0x5F);
    private static final Color ZEBRA = new Color(0xF3, 0xF5, 0xF8);
    private static final Font TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, HEAD_BG);
    private static final Font LABEL = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f);
    private static final Font TEXT = FontFactory.getFont(FontFactory.HELVETICA, 8.5f);
    private static final Font SMALL = FontFactory.getFont(FontFactory.HELVETICA, 7, Color.GRAY);
    private static final Font TH = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE);

    private final StatementProperties props;

    public PdfRenderer(StatementProperties props) {
        this.props = props;
    }

    public byte[] render(Statement s) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = open(out);
        doc.add(new Paragraph(props.bankName(), TITLE));
        doc.add(new Paragraph("Statement of account", LABEL));
        doc.add(new Paragraph(" ", TEXT));
        account(doc, s);
        doc.close();
        return out.toByteArray();
    }

    /** One file for the customer: a summary page, then each account's statement on its own page. */
    public byte[] render(CustomerStatement cs) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = open(out);
        doc.add(new Paragraph(props.bankName(), TITLE));
        doc.add(new Paragraph("Customer statement: all accounts", LABEL));
        doc.add(new Paragraph(" ", TEXT));

        PdfPTable head = new PdfPTable(new float[]{1.2f, 2.3f, 1.2f, 2.3f});
        head.setWidthPercentage(100);
        kv(head, "Customer no.", cs.customer());
        kv(head, "Period", fmt(cs.from()) + " to " + fmt(cs.to()));
        kv(head, "Customer name", cs.name());
        kv(head, "Accounts", String.valueOf(cs.statements().size()));
        doc.add(head);
        doc.add(new Paragraph(" ", TEXT));

        PdfPTable t = new PdfPTable(new float[]{1.6f, 2.6f, 0.8f, 1.5f, 1.4f, 1.4f, 1.5f});
        t.setWidthPercentage(100);
        t.setHeaderRows(1);
        String[] cols = {"Account", "Account name", "Ccy", "Opening", "Debits", "Credits", "Closing"};
        for (int i = 0; i < cols.length; i++) t.addCell(th(cols[i], i >= 3 ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT));
        int row = 0;
        for (Statement s : cs.statements()) {
            boolean z = (row++ % 2) == 1;
            t.addCell(cell(s.account().id(), false, z));
            t.addCell(cell(!s.account().title().isBlank() ? s.account().title() : s.account().shortTitle(), false, z));
            t.addCell(cell(s.account().currency(), false, z));
            t.addCell(cell(money(s.opening()), true, z));
            t.addCell(cell(money(s.totalDebits()), true, z));
            t.addCell(cell(money(s.totalCredits()), true, z));
            t.addCell(cell(money(s.closing()), true, z));
        }
        doc.add(t);
        doc.add(new Paragraph(" ", TEXT));

        PdfPTable tot = new PdfPTable(new float[]{1.2f, 1f, 1.6f, 1.5f, 1.5f, 1.6f});
        tot.setWidthPercentage(100);
        String[] tc = {"Currency", "Accounts", "Opening", "Debits", "Credits", "Closing"};
        for (int i = 0; i < tc.length; i++) tot.addCell(th(tc[i], i >= 2 ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT));
        for (CustomerStatement.CurrencyTotal c : cs.totals()) {
            tot.addCell(cell(c.currency(), false, false));
            tot.addCell(cell(String.valueOf(c.accounts()), false, false));
            tot.addCell(cell(money(c.opening()), true, false));
            tot.addCell(cell(money(c.totalDebits()), true, false));
            tot.addCell(cell(money(c.totalCredits()), true, false));
            tot.addCell(cell(money(c.closing()), true, false));
        }
        doc.add(new Paragraph("Totals per currency", LABEL));
        doc.add(new Paragraph(" ", SMALL));
        doc.add(tot);
        for (CustomerStatement.Skipped sk : cs.skipped()) {
            doc.add(new Paragraph("Not included: account " + sk.account() + " (" + sk.reason() + ")", SMALL));
        }

        for (Statement s : cs.statements()) {
            doc.newPage();
            doc.add(new Paragraph("Account " + s.account().id() + " · " + s.account().currency(), TITLE));
            doc.add(new Paragraph(" ", TEXT));
            account(doc, s);
        }
        doc.close();
        return out.toByteArray();
    }

    private Document open(ByteArrayOutputStream out) {
        Document doc = new Document(PageSize.A4, 36, 36, 40, 48);
        PdfWriter writer = PdfWriter.getInstance(doc, out);
        String generated = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm"));
        writer.setPageEvent(new PdfPageEventHelper() {
            @Override
            public void onEndPage(PdfWriter w, Document d) {
                ColumnText.showTextAligned(w.getDirectContent(), Element.ALIGN_LEFT,
                        new Phrase("Generated " + generated + " from the core banking system (read-only)", SMALL),
                        d.left(), d.bottom() - 20, 0);
                ColumnText.showTextAligned(w.getDirectContent(), Element.ALIGN_RIGHT,
                        new Phrase("Page " + w.getPageNumber(), SMALL), d.right(), d.bottom() - 20, 0);
            }
        });
        doc.open();
        return doc;
    }

    private void account(Document doc, Statement s) {
        PdfPTable head = new PdfPTable(new float[]{1.2f, 2.3f, 1.2f, 2.3f});
        head.setWidthPercentage(100);
        String name = !s.account().title().isBlank() ? s.account().title() : s.account().shortTitle();
        kv(head, "Account", s.account().id());
        kv(head, "Period", fmt(s.from()) + " to " + fmt(s.to()));
        kv(head, "Account name", name);
        kv(head, "Currency", s.account().currency());
        kv(head, "Customer no.", s.account().customer());
        kv(head, "Product", s.account().category());
        doc.add(head);
        doc.add(new Paragraph(" ", TEXT));

        PdfPTable sum = new PdfPTable(4);
        sum.setWidthPercentage(100);
        for (String h : new String[]{"Opening balance", "Total debits", "Total credits", "Closing balance"}) {
            sum.addCell(th(h, Element.ALIGN_CENTER));
        }
        for (BigDecimal v : new BigDecimal[]{s.opening(), s.totalDebits(), s.totalCredits(), s.closing()}) {
            PdfPCell c = new PdfPCell(new Phrase(money(v), LABEL));
            c.setHorizontalAlignment(Element.ALIGN_CENTER);
            c.setPadding(5);
            c.setBorderColor(Color.LIGHT_GRAY);
            sum.addCell(c);
        }
        doc.add(sum);
        doc.add(new Paragraph(" ", TEXT));

        PdfPTable t = new PdfPTable(new float[]{1.1f, 1.1f, 3.4f, 1.9f, 1.3f, 1.3f, 1.4f});
        t.setWidthPercentage(100);
        t.setHeaderRows(1);
        String[] cols = {"Booking date", "Value date", "Description", "Reference", "Debit", "Credit", "Balance"};
        for (int i = 0; i < cols.length; i++) t.addCell(th(cols[i], i >= 4 ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT));
        t.addCell(cell(fmt(s.from()), false, false));
        t.addCell(cell("", false, false));
        t.addCell(cell("Opening balance", false, false));
        t.addCell(cell("", false, false));
        t.addCell(cell("", true, false));
        t.addCell(cell("", true, false));
        t.addCell(cell(money(s.opening()), true, false));
        int row = 0;
        for (Statement.Line l : s.lines()) {
            boolean z = (row++ % 2) == 1;
            t.addCell(cell(fmt(l.bookingDate()), false, z));
            t.addCell(cell(fmt(l.valueDate()), false, z));
            t.addCell(cell(l.description() + (l.reversal() ? " (reversal)" : ""), false, z));
            t.addCell(cell(l.reference(), false, z));
            t.addCell(cell(l.debit() == null ? "" : money(l.debit()), true, z));
            t.addCell(cell(l.credit() == null ? "" : money(l.credit()), true, z));
            t.addCell(cell(money(l.balance()), true, z));
        }
        if (s.lines().isEmpty()) {
            PdfPCell none = cell("No transactions in this period", false, false);
            none.setColspan(7);
            t.addCell(none);
        }
        doc.add(t);
        doc.add(new Paragraph(" ", TEXT));
        Paragraph close = new Paragraph("Closing balance " + fmt(s.to()) + ": " + money(s.closing()) + " " + s.account().currency(), LABEL);
        close.setAlignment(Element.ALIGN_RIGHT);
        doc.add(close);
    }

    private static void kv(PdfPTable t, String k, String v) {
        PdfPCell a = new PdfPCell(new Phrase(k, LABEL));
        PdfPCell b = new PdfPCell(new Phrase(v == null ? "" : v, TEXT));
        for (PdfPCell c : new PdfPCell[]{a, b}) {
            c.setBorder(Rectangle.NO_BORDER);
            c.setPaddingBottom(3);
            t.addCell(c);
        }
    }

    private static PdfPCell th(String text, int align) {
        PdfPCell c = new PdfPCell(new Phrase(text, TH));
        c.setBackgroundColor(HEAD_BG);
        c.setHorizontalAlignment(align);
        c.setPadding(4);
        c.setBorderColor(HEAD_BG);
        return c;
    }

    private static PdfPCell cell(String text, boolean right, boolean zebra) {
        PdfPCell c = new PdfPCell(new Phrase(text == null ? "" : text, TEXT));
        c.setHorizontalAlignment(right ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
        c.setPadding(3);
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderColor(new Color(0xDD, 0xE1, 0xE6));
        if (zebra) c.setBackgroundColor(ZEBRA);
        return c;
    }

    private static String fmt(LocalDate d) {
        return d == null ? "" : d.format(DATE);
    }

    private static String money(BigDecimal v) {
        if (v == null) return "";
        return new DecimalFormat("#,##0.00").format(v.setScale(2, RoundingMode.HALF_UP));
    }
}
