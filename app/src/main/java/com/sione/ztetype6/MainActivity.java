package com.sione.ztetype6;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.sione.ztetype6.crypto.ZteType6Codec;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final int REQ_BIN = 1001;
    private static final int REQ_XML = 1002;
    private static final int REQ_TEMPLATE = 1003;
    private static final int REQ_SAVE = 1004;

    private static final int MODE_DECRYPT = 0;
    private static final int MODE_EDITOR = 1;
    private static final int MODE_ENCRYPT = 2;

    private static final String[] ROUTER_MODEL_LABELS = {
            "Pilih model router…",
            "ZTE F6600P",
            "ZTE F670L",
            "ZTE F672Y",
            "ZTE F679D"
    };

    private static final String[] ROUTER_MODEL_KEYS = {
            "",
            "F6600P",
            "F670L",
            "F672Y",
            "F679D"
    };

    private Spinner routerModelSpinner;
    private EditText serialInput;
    private EditText macInput;
    private EditText xmlEditor;
    private EditText searchInput;

    private TextView selectedBin;
    private TextView selectedXml;
    private TextView selectedTemplate;
    private TextView editorSummary;
    private TextView searchStatus;
    private TextView status;

    private LinearLayout decryptPanel;
    private LinearLayout editorPanel;
    private LinearLayout encryptPanel;
    private Button decryptTab;
    private Button editorTab;
    private Button encryptTab;
    private Button decryptButton;
    private Button editorEncryptButton;
    private Button encryptButton;

    private Uri binUri;
    private Uri xmlUri;
    private Uri templateUri;
    private byte[] templateBinInMemory;
    private String activeRouterModel = "";
    private String templateRouterModel = "";

    private byte[] pendingOutput;
    private String pendingName;
    private String pendingMime;
    private String pendingSavedMessage;

    private Charset editorCharset = StandardCharsets.UTF_8;
    private byte[] editorBom = new byte[0];

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("identity", MODE_PRIVATE);
        setContentView(buildUi());
        serialInput.setText(prefs.getString("serial", ""));
        macInput.setText(prefs.getString("mac", ""));
        showMode(MODE_DECRYPT);
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private View buildUi() {
        int pad = dp(18);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(245, 247, 251));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, dp(20), pad, dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = text("ZTE Config Type 6", 26, Color.rgb(23, 32, 51), true);
        root.addView(title);
        TextView subtitle = text("Workflow release v1.0.0.1 + XML viewer/editor offline.", 14,
                Color.rgb(102, 112, 133), false);
        subtitle.setPadding(0, dp(4), 0, dp(18));
        root.addView(subtitle);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(dp(4), dp(4), dp(4), dp(4));
        tabs.setBackground(rounded(Color.WHITE, 14, Color.rgb(216, 222, 233), 1));

        decryptTab = actionButton("Decrypt", false);
        editorTab = actionButton("XML Editor", false);
        encryptTab = actionButton("Encrypt", false);
        decryptTab.setOnClickListener(v -> showMode(MODE_DECRYPT));
        editorTab.setOnClickListener(v -> showMode(MODE_EDITOR));
        encryptTab.setOnClickListener(v -> showMode(MODE_ENCRYPT));
        tabs.addView(decryptTab, new LinearLayout.LayoutParams(0, dp(48), 1));
        tabs.addView(editorTab, new LinearLayout.LayoutParams(0, dp(48), 1));
        tabs.addView(encryptTab, new LinearLayout.LayoutParams(0, dp(48), 1));
        root.addView(tabs);

        LinearLayout identity = card();
        identity.setPadding(pad, pad, pad, pad);
        identity.addView(sectionTitle("Router & identitas"));
        identity.addView(helper("Pilih model router sesuai paket release v1.0.0.1, lalu isi SN + MAC. Pilihan model wajib sebelum decrypt dan ikut mengikat template sesi agar tidak tertukar antar-router."));

        TextView modelLabel = text("Model router", 13, Color.rgb(23, 32, 51), true);
        identity.addView(modelLabel);
        addGap(identity, 6);
        routerModelSpinner = new Spinner(this);
        ArrayAdapter<String> modelAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, ROUTER_MODEL_LABELS);
        modelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        routerModelSpinner.setAdapter(modelAdapter);
        routerModelSpinner.setSelection(0);
        routerModelSpinner.setPadding(dp(10), 0, dp(10), 0);
        routerModelSpinner.setBackground(rounded(Color.rgb(250, 251, 253), 12, Color.rgb(216, 222, 233), 1));
        identity.addView(routerModelSpinner, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
        addGap(identity, 12);

        serialInput = input("Serial Number (12 atau 19 karakter)");
        macInput = input("MAC Address (AA:BB:CC:00:11:22)");
        macInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        identity.addView(serialInput);
        addGap(identity, 10);
        identity.addView(macInput);
        setTopMargin(identity, dp(14));
        root.addView(identity);

        decryptPanel = buildDecryptPanel(pad);
        setTopMargin(decryptPanel, dp(14));
        root.addView(decryptPanel);

        editorPanel = buildEditorPanel(pad);
        setTopMargin(editorPanel, dp(14));
        root.addView(editorPanel);

        encryptPanel = buildEncryptPanel(pad);
        setTopMargin(encryptPanel, dp(14));
        root.addView(encryptPanel);

        LinearLayout statusCard = card();
        statusCard.setPadding(pad, pad, pad, pad);
        statusCard.addView(sectionTitle("Status"));
        status = text("Siap. Semua proses dilakukan offline.", 14, Color.rgb(102, 112, 133), false);
        status.setPadding(0, dp(8), 0, 0);
        statusCard.addView(status);
        setTopMargin(statusCard, dp(14));
        root.addView(statusCard);

        TextView note = helper("Gunakan hanya pada router milik sendiri atau perangkat yang Anda memiliki izin untuk mengaudit. Selalu simpan config.bin asli sebelum restore hasil edit.");
        note.setPadding(0, dp(18), 0, 0);
        root.addView(note);

        return scroll;
    }

    private LinearLayout buildDecryptPanel(int pad) {
        LinearLayout panel = card();
        panel.setPadding(pad, pad, pad, pad);
        panel.addView(sectionTitle("Decrypt config.bin → XML"));
        panel.addView(helper("Pilih model ZTE yang sesuai (F6600P/F670L/F672Y/F679D), lalu pilih config.bin Type 6 dan isi SN + MAC. Hasil decrypt langsung dibuka di XML Editor dan BIN yang sama otomatis menjadi template encrypt untuk model tersebut."));

        selectedBin = fileLabel("Belum ada config.bin dipilih");
        panel.addView(selectedBin);
        addGap(panel, 10);

        Button pickBin = secondaryButton("Pilih config.bin");
        pickBin.setOnClickListener(v -> openFile(REQ_BIN, "application/octet-stream",
                new String[]{"application/octet-stream", "*/*"}));
        panel.addView(pickBin);
        addGap(panel, 12);

        decryptButton = actionButton("Decrypt & buka XML Editor", true);
        decryptButton.setOnClickListener(v -> decryptNow());
        panel.addView(decryptButton);
        return panel;
    }

    private LinearLayout buildEditorPanel(int pad) {
        LinearLayout panel = card();
        panel.setPadding(pad, pad, pad, pad);
        panel.addView(sectionTitle("XML Viewer / Editor"));
        panel.addView(helper("Cari dan edit isi konfigurasi langsung di aplikasi. Quick Find disediakan untuk PPPoE/PPPIF, DevAuthInfo, username, dan password."));

        editorSummary = fileLabel("Belum ada XML dimuat.");
        panel.addView(editorSummary);
        addGap(panel, 10);

        HorizontalScrollView quickScroll = new HorizontalScrollView(this);
        quickScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout quick = new LinearLayout(this);
        quick.setOrientation(LinearLayout.HORIZONTAL);
        quickScroll.addView(quick);
        addQuickSearch(quick, "PPPIF", "PPPIF");
        addQuickSearch(quick, "WANCPPP", "WANCPPP");
        addQuickSearch(quick, "DevAuthInfo", "DevAuthInfo");
        addQuickSearch(quick, "Username", "name=\"User\"");
        addQuickSearch(quick, "Password", "name=\"Pass\"");
        panel.addView(quickScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
        addGap(panel, 10);

        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchInput = input("Cari teks di XML…");
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(0, dp(48), 1);
        searchInput.setLayoutParams(searchParams);
        searchInput.setSingleLine(true);
        searchRow.addView(searchInput);

        Button prev = smallButton("‹");
        prev.setOnClickListener(v -> findInEditor(false));
        LinearLayout.LayoutParams prevParams = new LinearLayout.LayoutParams(dp(52), dp(48));
        prevParams.leftMargin = dp(8);
        searchRow.addView(prev, prevParams);

        Button next = smallButton("›");
        next.setOnClickListener(v -> findInEditor(true));
        LinearLayout.LayoutParams nextParams = new LinearLayout.LayoutParams(dp(52), dp(48));
        nextParams.leftMargin = dp(6);
        searchRow.addView(next, nextParams);
        panel.addView(searchRow);

        searchStatus = helper("Pencarian belum dijalankan.");
        searchStatus.setPadding(0, dp(6), 0, dp(8));
        panel.addView(searchStatus);

        xmlEditor = new EditText(this);
        xmlEditor.setGravity(Gravity.TOP | Gravity.START);
        xmlEditor.setTypeface(Typeface.MONOSPACE);
        xmlEditor.setTextSize(12);
        xmlEditor.setTextColor(Color.rgb(28, 36, 52));
        xmlEditor.setHintTextColor(Color.rgb(145, 153, 170));
        xmlEditor.setHint("Hasil decrypt atau XML yang dipilih akan muncul di sini…");
        xmlEditor.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        xmlEditor.setHorizontallyScrolling(true);
        xmlEditor.setHorizontalScrollBarEnabled(true);
        xmlEditor.setVerticalScrollBarEnabled(true);
        xmlEditor.setPadding(dp(12), dp(12), dp(12), dp(12));
        xmlEditor.setBackground(rounded(Color.rgb(250, 251, 253), 10, Color.rgb(216, 222, 233), 1));
        panel.addView(xmlEditor, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(420)));
        addGap(panel, 12);

        LinearLayout toolRow = new LinearLayout(this);
        toolRow.setOrientation(LinearLayout.HORIZONTAL);
        Button validate = secondaryButton("Validasi XML");
        validate.setOnClickListener(v -> validateEditor());
        Button saveXml = secondaryButton("Simpan XML");
        saveXml.setOnClickListener(v -> saveEditorXml());
        toolRow.addView(validate, new LinearLayout.LayoutParams(0, dp(48), 1));
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(0, dp(48), 1);
        saveParams.leftMargin = dp(8);
        toolRow.addView(saveXml, saveParams);
        panel.addView(toolRow);
        addGap(panel, 8);

        LinearLayout extractRow = new LinearLayout(this);
        extractRow.setOrientation(LinearLayout.HORIZONTAL);
        Button pppExtract = secondaryButton("Simpan PPPoE .txt");
        pppExtract.setOnClickListener(v -> savePppoeExtract());
        Button authExtract = secondaryButton("Simpan DevAuthInfo .txt");
        authExtract.setOnClickListener(v -> saveDevAuthExtract());
        extractRow.addView(pppExtract, new LinearLayout.LayoutParams(0, dp(48), 1));
        LinearLayout.LayoutParams authParams = new LinearLayout.LayoutParams(0, dp(48), 1);
        authParams.leftMargin = dp(8);
        extractRow.addView(authExtract, authParams);
        panel.addView(extractRow);
        addGap(panel, 12);

        editorEncryptButton = actionButton("Encrypt XML editor → config_new.bin", true);
        editorEncryptButton.setOnClickListener(v -> encryptEditorNow());
        panel.addView(editorEncryptButton);
        return panel;
    }

    private LinearLayout buildEncryptPanel(int pad) {
        LinearLayout panel = card();
        panel.setPadding(pad, pad, pad, pad);
        panel.addView(sectionTitle("Encrypt XML → config.bin"));
        panel.addView(helper("Mode file terpisah. Untuk alur paling cepat setelah decrypt, gunakan tombol Encrypt langsung dari tab XML Editor."));

        selectedXml = fileLabel("Belum ada XML dipilih");
        panel.addView(selectedXml);
        addGap(panel, 10);
        Button pickXml = secondaryButton("Pilih XML");
        pickXml.setOnClickListener(v -> openFile(REQ_XML, "text/xml",
                new String[]{"text/xml", "application/xml", "*/*"}));
        panel.addView(pickXml);
        addGap(panel, 14);

        selectedTemplate = fileLabel("Belum ada template config.bin dipilih");
        panel.addView(selectedTemplate);
        addGap(panel, 10);
        Button pickTemplate = secondaryButton("Pilih config.bin asli sebagai template");
        pickTemplate.setOnClickListener(v -> openFile(REQ_TEMPLATE, "application/octet-stream",
                new String[]{"application/octet-stream", "*/*"}));
        panel.addView(pickTemplate);
        addGap(panel, 12);

        encryptButton = actionButton("Encrypt file XML & simpan BIN", true);
        encryptButton.setOnClickListener(v -> encryptFileNow());
        panel.addView(encryptButton);
        return panel;
    }

    private void showMode(int mode) {
        decryptPanel.setVisibility(mode == MODE_DECRYPT ? View.VISIBLE : View.GONE);
        editorPanel.setVisibility(mode == MODE_EDITOR ? View.VISIBLE : View.GONE);
        encryptPanel.setVisibility(mode == MODE_ENCRYPT ? View.VISIBLE : View.GONE);
        styleTab(decryptTab, mode == MODE_DECRYPT);
        styleTab(editorTab, mode == MODE_EDITOR);
        styleTab(encryptTab, mode == MODE_ENCRYPT);
    }

    private void styleTab(Button button, boolean active) {
        button.setTextColor(active ? Color.WHITE : Color.rgb(23, 32, 51));
        button.setBackground(rounded(active ? Color.rgb(23, 105, 224) : Color.TRANSPARENT,
                11, Color.TRANSPARENT, 0));
    }

    private void decryptNow() {
        String routerModel = selectedRouterModel();
        if (routerModel.isEmpty()) {
            toast("Pilih model router terlebih dahulu.");
            return;
        }
        if (binUri == null) {
            toast("Pilih config.bin terlebih dahulu.");
            return;
        }
        String serial = serialInput.getText().toString().trim();
        String mac = macInput.getText().toString().trim();
        saveIdentity(serial, mac);
        setBusy(true, "Mendekripsi " + routerModel + " · config.bin Type 6…");

        executor.submit(() -> {
            try {
                byte[] input = readAll(binUri);
                ZteType6Codec.DecodeResult result = ZteType6Codec.decrypt(input, serial, mac);
                templateBinInMemory = input;
                templateUri = binUri;
                activeRouterModel = routerModel;
                templateRouterModel = routerModel;
                runOnUiThread(() -> {
                    loadEditorBytes(result.xml());
                    selectedTemplate.setText("Template: " + displayName(binUri)
                            + " · " + routerModel + " (otomatis dari decrypt)");
                    setBusy(false, "Decrypt " + routerModel + " berhasil · Type " + result.payloadType()
                            + (result.signature().isEmpty() ? "" : " · " + result.signature())
                            + (result.sourceOffset() == 0 ? "" : " · offset " + result.sourceOffset()));
                    showMode(MODE_EDITOR);
                    toast("Decrypt " + routerModel + " berhasil. XML siap diedit.");
                });
            } catch (Exception ex) {
                runOnUiThread(() -> fail(ex));
            }
        });
    }

    private void encryptEditorNow() {
        String routerModel = selectedRouterModel();
        if (routerModel.isEmpty()) {
            toast("Pilih model router terlebih dahulu.");
            return;
        }
        if (xmlEditor.getText().length() == 0) {
            toast("Belum ada XML di editor.");
            return;
        }
        String xmlText = xmlEditor.getText().toString();
        byte[] xmlBytes = encodeEditorText(xmlText);
        String serial = serialInput.getText().toString().trim();
        String mac = macInput.getText().toString().trim();
        saveIdentity(serial, mac);
        setBusy(true, "Validasi XML lalu encrypt untuk " + routerModel + "…");

        executor.submit(() -> {
            try {
                ensureTemplateModel(routerModel);
                XmlConfigTools.validateWellFormed(xmlText);
                byte[] template = getTemplateBytes();
                ZteType6Codec.EncodeResult result = ZteType6Codec.encrypt(xmlBytes, template, serial, mac);
                activeRouterModel = routerModel;
                runOnUiThread(() -> {
                    setBusy(false, "Encrypt editor " + routerModel + " berhasil · round-trip verification: OK");
                    queueSave(result.bin(), "application/octet-stream", "config_new.bin",
                            "config_new.bin " + routerModel + " berhasil disimpan.");
                });
            } catch (Exception ex) {
                runOnUiThread(() -> fail(ex));
            }
        });
    }

    private void encryptFileNow() {
        String routerModel = selectedRouterModel();
        if (routerModel.isEmpty()) {
            toast("Pilih model router terlebih dahulu.");
            return;
        }
        if (xmlUri == null) {
            toast("Pilih file XML terlebih dahulu.");
            return;
        }
        if (templateUri == null && templateBinInMemory == null) {
            toast("Pilih config.bin asli sebagai template.");
            return;
        }
        String serial = serialInput.getText().toString().trim();
        String mac = macInput.getText().toString().trim();
        saveIdentity(serial, mac);
        setBusy(true, "Mengenkripsi XML untuk " + routerModel + " dan melakukan round-trip verification…");

        executor.submit(() -> {
            try {
                ensureTemplateModel(routerModel);
                byte[] xml = readAll(xmlUri);
                XmlConfigTools.validateWellFormed(decodeXmlForValidation(xml));
                byte[] template = getTemplateBytes();
                ZteType6Codec.EncodeResult result = ZteType6Codec.encrypt(xml, template, serial, mac);
                activeRouterModel = routerModel;
                runOnUiThread(() -> {
                    setBusy(false, "Encrypt " + routerModel + " berhasil · round-trip verification: OK");
                    queueSave(result.bin(), "application/octet-stream", "config_new.bin",
                            "config_new.bin " + routerModel + " berhasil disimpan.");
                });
            } catch (Exception ex) {
                runOnUiThread(() -> fail(ex));
            }
        });
    }

    private byte[] getTemplateBytes() throws Exception {
        if (templateBinInMemory != null) return templateBinInMemory;
        if (templateUri == null) throw new IllegalStateException("Template config.bin belum dipilih.");
        return readAll(templateUri);
    }

    private String selectedRouterModel() {
        if (routerModelSpinner == null) return "";
        int position = routerModelSpinner.getSelectedItemPosition();
        if (position <= 0 || position >= ROUTER_MODEL_KEYS.length) return "";
        return ROUTER_MODEL_KEYS[position];
    }

    private void ensureTemplateModel(String selectedModel) {
        if (selectedModel == null || selectedModel.isEmpty()) {
            throw new IllegalArgumentException("Model router belum dipilih.");
        }
        if (templateRouterModel != null && !templateRouterModel.isEmpty()
                && !templateRouterModel.equals(selectedModel)) {
            throw new IllegalArgumentException("Template BIN terikat ke " + templateRouterModel
                    + ", tetapi model yang dipilih sekarang " + selectedModel
                    + ". Pilih model yang sesuai atau pilih ulang template config.bin.");
        }
        if (templateRouterModel == null || templateRouterModel.isEmpty()) {
            templateRouterModel = selectedModel;
        }
    }

    private void validateEditor() {
        String xml = xmlEditor.getText().toString();
        if (xml.trim().isEmpty()) {
            toast("Belum ada XML di editor.");
            return;
        }
        setBusy(true, "Memvalidasi struktur XML…");
        executor.submit(() -> {
            try {
                XmlConfigTools.validateWellFormed(xml);
                runOnUiThread(() -> setBusy(false, "XML valid dan well-formed."));
            } catch (Exception ex) {
                runOnUiThread(() -> fail(new IllegalArgumentException("XML tidak valid: " + safeMessage(ex), ex)));
            }
        });
    }

    private void saveEditorXml() {
        if (xmlEditor.getText().length() == 0) {
            toast("Belum ada XML di editor.");
            return;
        }
        byte[] data = encodeEditorText(xmlEditor.getText().toString());
        queueSave(data, "text/xml", "config_decrypted.xml", "XML berhasil disimpan.");
    }

    private void savePppoeExtract() {
        String xml = xmlEditor.getText().toString();
        if (xml.trim().isEmpty()) {
            toast("Belum ada XML di editor.");
            return;
        }
        String extract = XmlConfigTools.extractPppoe(xml);
        queueSave(extract.getBytes(StandardCharsets.UTF_8), "text/plain", "pppif_extracted.txt",
                "Ekstrak PPPoE berhasil disimpan.");
    }

    private void saveDevAuthExtract() {
        String xml = xmlEditor.getText().toString();
        if (xml.trim().isEmpty()) {
            toast("Belum ada XML di editor.");
            return;
        }
        String extract = XmlConfigTools.extractActiveDevAuthInfo(xml);
        queueSave(extract.getBytes(StandardCharsets.UTF_8), "text/plain", "devauthinfo_extracted.txt",
                "Ekstrak DevAuthInfo berhasil disimpan.");
    }

    private void addQuickSearch(LinearLayout parent, String label, String query) {
        Button button = smallButton(label);
        button.setOnClickListener(v -> {
            searchInput.setText(query);
            findInEditor(true);
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40));
        params.rightMargin = dp(7);
        parent.addView(button, params);
    }

    private void findInEditor(boolean forward) {
        String query = searchInput.getText().toString();
        String text = xmlEditor.getText().toString();
        if (query.isEmpty()) {
            searchStatus.setText("Masukkan kata kunci pencarian.");
            return;
        }
        int from = forward ? Math.max(xmlEditor.getSelectionEnd(), 0) : Math.max(xmlEditor.getSelectionStart(), 0);
        XmlConfigTools.SearchResult result = XmlConfigTools.find(text, query, from, forward);
        if (result.start() < 0) {
            searchStatus.setText("Tidak ditemukan: " + query);
            return;
        }
        xmlEditor.requestFocus();
        xmlEditor.setSelection(result.start(), Math.min(result.end(), text.length()));
        searchStatus.setText("Hasil " + result.ordinal() + " dari " + result.total() + " · " + query);
    }

    private void loadEditorBytes(byte[] data) {
        DecodedText decoded = decodeXmlText(data);
        editorCharset = decoded.charset;
        editorBom = decoded.bom;
        xmlEditor.setText(decoded.text);
        xmlEditor.setSelection(0);
        String modelInfo = activeRouterModel == null || activeRouterModel.isEmpty()
                ? "" : " · Router " + activeRouterModel;
        editorSummary.setText("XML dimuat · " + data.length + " byte · " + editorCharset.displayName()
                + modelInfo + "\n" + XmlConfigTools.targetSummary(decoded.text));
        searchStatus.setText("Gunakan Quick Find atau ketik kata kunci sendiri.");
    }

    private byte[] encodeEditorText(String text) {
        byte[] body = text.getBytes(editorCharset);
        if (editorBom.length == 0) return body;
        byte[] out = Arrays.copyOf(editorBom, editorBom.length + body.length);
        System.arraycopy(body, 0, out, editorBom.length, body.length);
        return out;
    }

    private String decodeXmlForValidation(byte[] data) {
        return decodeXmlText(data).text;
    }

    private DecodedText decodeXmlText(byte[] data) {
        Charset charset = StandardCharsets.UTF_8;
        byte[] bom = new byte[0];
        int offset = 0;

        if (data.length >= 3 && (data[0] & 0xFF) == 0xEF && (data[1] & 0xFF) == 0xBB && (data[2] & 0xFF) == 0xBF) {
            charset = StandardCharsets.UTF_8;
            bom = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
            offset = 3;
        } else if (data.length >= 2 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xFE) {
            charset = StandardCharsets.UTF_16LE;
            bom = new byte[]{(byte) 0xFF, (byte) 0xFE};
            offset = 2;
        } else if (data.length >= 2 && (data[0] & 0xFF) == 0xFE && (data[1] & 0xFF) == 0xFF) {
            charset = StandardCharsets.UTF_16BE;
            bom = new byte[]{(byte) 0xFE, (byte) 0xFF};
            offset = 2;
        } else {
            int sniffLength = Math.min(data.length, 256);
            String sniff = new String(data, 0, sniffLength, StandardCharsets.ISO_8859_1);
            Matcher matcher = Pattern.compile("(?i)encoding\\s*=\\s*[\"']([^\"']+)[\"']").matcher(sniff);
            if (matcher.find()) {
                try {
                    charset = Charset.forName(matcher.group(1));
                } catch (Exception ignored) {
                    charset = StandardCharsets.UTF_8;
                }
            }
        }

        return new DecodedText(new String(data, offset, data.length - offset, charset), charset, bom);
    }

    private static final class DecodedText {
        final String text;
        final Charset charset;
        final byte[] bom;

        DecodedText(String text, Charset charset, byte[] bom) {
            this.text = text;
            this.charset = charset;
            this.bom = bom;
        }
    }

    private void fail(Exception ex) {
        setBusy(false, "Gagal: " + safeMessage(ex));
        Toast.makeText(this, safeMessage(ex), Toast.LENGTH_LONG).show();
    }

    private String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.trim().isEmpty() ? throwable.getClass().getSimpleName() : message;
    }

    private void setBusy(boolean busy, String message) {
        if (decryptButton != null) decryptButton.setEnabled(!busy);
        if (editorEncryptButton != null) editorEncryptButton.setEnabled(!busy);
        if (encryptButton != null) encryptButton.setEnabled(!busy);
        status.setText(message);
        int color;
        if (busy) color = Color.rgb(23, 105, 224);
        else if (message.startsWith("Gagal")) color = Color.rgb(180, 35, 24);
        else color = Color.rgb(22, 121, 74);
        status.setTextColor(color);
    }

    private void saveIdentity(String serial, String mac) {
        prefs.edit().putString("serial", serial).putString("mac", mac).apply();
    }

    private void openFile(int requestCode, String type, String[] mimeTypes) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(type);
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        startActivityForResult(intent, requestCode);
    }

    private void queueSave(byte[] output, String mime, String name, String savedMessage) {
        pendingOutput = output;
        pendingMime = mime;
        pendingName = name;
        pendingSavedMessage = savedMessage;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mime);
        intent.putExtra(Intent.EXTRA_TITLE, name);
        startActivityForResult(intent, REQ_SAVE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (Exception ignored) {}

        if (requestCode == REQ_BIN) {
            binUri = uri;
            templateUri = uri;
            templateBinInMemory = null;
            templateRouterModel = "";
            String name = displayName(uri);
            selectedBin.setText("BIN: " + name);
            selectedTemplate.setText("Template: " + name + " (menunggu decrypt/model)");
        } else if (requestCode == REQ_XML) {
            xmlUri = uri;
            selectedXml.setText("XML: " + displayName(uri));
            setBusy(true, "Memuat XML ke editor…");
            String selectedModelForXml = selectedRouterModel();
            executor.submit(() -> {
                try {
                    byte[] xml = readAll(uri);
                    activeRouterModel = selectedModelForXml;
                    runOnUiThread(() -> {
                        loadEditorBytes(xml);
                        setBusy(false, "XML berhasil dimuat ke editor.");
                        showMode(MODE_EDITOR);
                    });
                } catch (Exception ex) {
                    runOnUiThread(() -> fail(ex));
                }
            });
        } else if (requestCode == REQ_TEMPLATE) {
            templateUri = uri;
            templateBinInMemory = null;
            templateRouterModel = selectedRouterModel();
            String suffix = templateRouterModel.isEmpty() ? "" : " · " + templateRouterModel;
            selectedTemplate.setText("Template: " + displayName(uri) + suffix);
        } else if (requestCode == REQ_SAVE && pendingOutput != null) {
            byte[] output = pendingOutput;
            String savedMessage = pendingSavedMessage == null ? "File berhasil disimpan." : pendingSavedMessage;
            pendingOutput = null;
            pendingName = null;
            pendingMime = null;
            pendingSavedMessage = null;
            executor.submit(() -> {
                try (OutputStream os = getContentResolver().openOutputStream(uri, "w")) {
                    if (os == null) throw new IllegalStateException("Tidak dapat membuka file output.");
                    os.write(output);
                    os.flush();
                    runOnUiThread(() -> {
                        status.setText(savedMessage + " · " + displayName(uri));
                        status.setTextColor(Color.rgb(22, 121, 74));
                        toast(savedMessage);
                    });
                } catch (Exception ex) {
                    runOnUiThread(() -> fail(ex));
                }
            });
        }
    }

    private byte[] readAll(Uri uri) throws Exception {
        ContentResolver resolver = getContentResolver();
        try (InputStream is = resolver.openInputStream(uri)) {
            if (is == null) throw new IllegalStateException("File tidak dapat dibuka.");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = is.read(buffer)) != -1) out.write(buffer, 0, read);
            return out.toByteArray();
        }
    }

    private String displayName(Uri uri) {
        String result = uri.getLastPathSegment();
        try (Cursor cursor = getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) result = cursor.getString(index);
            }
        } catch (Exception ignored) {}
        return result == null ? "file" : result;
    }

    private LinearLayout card() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setBackground(rounded(Color.WHITE, 16, Color.rgb(216, 222, 233), 1));
        return view;
    }

    private TextView sectionTitle(String value) {
        return text(value, 17, Color.rgb(23, 32, 51), true);
    }

    private TextView helper(String value) {
        TextView view = text(value, 13, Color.rgb(102, 112, 133), false);
        view.setLineSpacing(0, 1.15f);
        view.setPadding(0, dp(6), 0, dp(12));
        return view;
    }

    private EditText input(String hint) {
        EditText edit = new EditText(this);
        edit.setHint(hint);
        edit.setSingleLine(true);
        edit.setTextSize(15);
        edit.setTextColor(Color.rgb(23, 32, 51));
        edit.setHintTextColor(Color.rgb(145, 153, 170));
        edit.setPadding(dp(14), 0, dp(14), 0);
        edit.setBackground(rounded(Color.rgb(250, 251, 253), 12, Color.rgb(216, 222, 233), 1));
        edit.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
        return edit;
    }

    private TextView fileLabel(String value) {
        TextView view = text(value, 14, Color.rgb(23, 32, 51), false);
        view.setPadding(dp(12), dp(12), dp(12), dp(12));
        view.setBackground(rounded(Color.rgb(250, 251, 253), 10, Color.rgb(216, 222, 233), 1));
        return view;
    }

    private Button secondaryButton(String label) {
        Button button = actionButton(label, false);
        button.setTextColor(Color.rgb(23, 105, 224));
        button.setBackground(rounded(Color.WHITE, 12, Color.rgb(23, 105, 224), 1));
        button.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        return button;
    }

    private Button smallButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(12);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setTextColor(Color.rgb(23, 105, 224));
        button.setBackground(rounded(Color.WHITE, 10, Color.rgb(23, 105, 224), 1));
        return button;
    }

    private Button actionButton(String label, boolean primary) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(12), 0, dp(12), 0);
        if (primary) {
            button.setTextColor(Color.WHITE);
            button.setBackground(rounded(Color.rgb(23, 105, 224), 12, Color.TRANSPARENT, 0));
            button.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));
        }
        return button;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private GradientDrawable rounded(int fill, int radiusDp, int strokeColor, int strokeDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) d.setStroke(dp(strokeDp), strokeColor);
        return d;
    }

    private void addGap(LinearLayout parent, int sizeDp) {
        View gap = new View(this);
        parent.addView(gap, new LinearLayout.LayoutParams(1, dp(sizeDp)));
    }

    private void setTopMargin(View view, int px) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = px;
        view.setLayoutParams(p);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
