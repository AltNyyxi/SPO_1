import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main extends JFrame {

    // ==== Типы токенов ====
    enum TokenType { Identifier, Number, StringLiteral, CharLiteral, Operator }

    static class Token {
        final TokenType type;
        final String value;
        Token(TokenType t, String v) { type = t; value = v; }
        @Override public String toString() { return type + "(" + value + ")"; }
    }

    // ==== Состояние ====
    private final Map<String, Integer> operands = new LinkedHashMap<>();
    private final Map<String, Integer> operators = new LinkedHashMap<>();
    private final Set<String> functionNames = new HashSet<>();
    private final Set<String> userTypes = new HashSet<>();

    private int progLen, progDict;
    private double progVolume;

    // ==== UI ====
    private final JTextField filePathField = new JTextField();
    private final JTextArea metricsArea = new JTextArea(12, 60);
    private final DefaultTableModel operatorsModel = new DefaultTableModel(new Object[]{"Оператор", "Кол-во"}, 0);
    private final DefaultTableModel operandsModel  = new DefaultTableModel(new Object[]{"Операнд",  "Кол-во"}, 0);

    // ==== Множества (ключевые слова Kotlin) ====

    // "Операторы-ключевые слова"
    private static final Set<String> KEYWORD_OPERATORS = new HashSet<>(Arrays.asList(
            "return", "break", "continue", "throw"
    ));

    // Части составных конструкций (сами по себе не операторы)
    private static final Set<String> COMPOUND_PART_KEYWORDS = new HashSet<>(Arrays.asList(
            "else", "catch", "finally"
    ));

    // Начала управляющих конструкций
    private static final Set<String> COMPOUND_STARTERS = new HashSet<>(Arrays.asList(
            "if", "when", "for", "while", "do", "try"
    ));

    // Модификаторы и ключевые слова, пропускаемые как операнды
    private static final Set<String> SKIP_KEYWORDS = new HashSet<>(Arrays.asList(
            // Объявления / ООП
            "package", "import", "class", "interface", "object", "enum",
            "fun", "val", "var", "typealias", "constructor", "init",
            "companion", "data", "sealed", "open", "abstract", "final",
            "override", "operator", "infix", "inline", "noinline",
            "crossinline", "reified", "suspend", "tailrec", "external",
            "annotation", "const", "lateinit", "inner",
            "public", "private", "protected", "internal",
            // Типы / значения
            "true", "false", "null", "this", "super",
            "is", "as", "in", "out", "by", "where",
            "Unit", "Nothing", "Any",
            // Прочие
            "get", "set", "field", "property", "receiver", "param",
            "setparam", "delegate", "file", "expect", "actual"
    ));

    // Встроенные типы Kotlin (для определения приведений и объявлений)
    private static final Set<String> TYPE_KEYWORDS = new HashSet<>(Arrays.asList(
            "Int", "Long", "Short", "Byte", "Float", "Double", "Boolean",
            "Char", "String", "Unit", "Any", "Nothing", "Number",
            "List", "MutableList", "Set", "MutableSet", "Map", "MutableMap",
            "Array", "IntArray", "LongArray", "DoubleArray", "BooleanArray",
            "CharArray", "ByteArray", "ShortArray", "FloatArray",
            "Pair", "Triple", "Sequence", "Iterable", "Collection",
            "Comparable", "Runnable", "Throwable", "Exception",
            "Error", "Function", "CharSequence"
    ));

    // Многосимвольные операторы (порядок важен — длинные раньше)
    private static final String[] MULTI_CHAR_OPS = new String[]{
            "===", "!==", "<<=", ">>=", "..<",
            "==", "!=", ">=", "<=", "&&", "||", "<<", ">>",
            "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=",
            "++", "--", "->", "::", "?.", "?:", "!!", ".."
    };

    public Main() {
        super("Парсер Kotlin — метрики Холстеда");
        initUI();
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1100, 700);
        setLocationRelativeTo(null);
    }

    private void initUI() {
        JPanel top = new JPanel(new BorderLayout(6, 6));
        JButton openBtn = new JButton("Открыть файл…");
        filePathField.setEditable(false);
        top.add(openBtn, BorderLayout.WEST);
        top.add(filePathField, BorderLayout.CENTER);
        top.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        JTable operatorsTable = new JTable(operatorsModel);
        JTable operandsTable  = new JTable(operandsModel);
        JScrollPane opSp = new JScrollPane(operatorsTable);
        JScrollPane odSp = new JScrollPane(operandsTable);
        opSp.setBorder(BorderFactory.createTitledBorder("Операторы"));
        odSp.setBorder(BorderFactory.createTitledBorder("Операнды"));

        JSplitPane tablesSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, opSp, odSp);
        tablesSplit.setResizeWeight(0.5);

        metricsArea.setEditable(false);
        metricsArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        JScrollPane metSp = new JScrollPane(metricsArea);
        metSp.setBorder(BorderFactory.createTitledBorder("Метрики"));

        JSplitPane mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tablesSplit, metSp);
        mainSplit.setResizeWeight(0.6);

        setLayout(new BorderLayout());
        add(top, BorderLayout.NORTH);
        add(mainSplit, BorderLayout.CENTER);

        openBtn.addActionListener(e -> chooseFile());
    }

    private void chooseFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Выберите файл с кодом Kotlin");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "Kotlin Source (*.kt;*.kts)", "kt", "kts"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            analyzeFile(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    // ================== Анализ ==================

    public void analyzeFile(String path) {
        File f = new File(path);
        if (!f.exists()) {
            JOptionPane.showMessageDialog(this, "Файл не найден: " + path);
            return;
        }

        operands.clear();
        operators.clear();
        functionNames.clear();
        userTypes.clear();
        progLen = progDict = 0;
        progVolume = 0;

        String raw;
        try {
            raw = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Ошибка чтения: " + ex.getMessage());
            return;
        }

        filePathField.setText(path);

        String code = removeCommentsAndPackage(raw);
        collectUserTypes(code);

        String masked = maskLiterals(code);
        collectFunctionNames(masked);

        List<String> bodies = extractFunctionBodies(code);
        String analysisCode = String.join("\n", bodies);

        List<Token> tokens = tokenize(analysisCode);
        analyzeTokens(tokens);
        displayResults();
    }

    /** Удаляем //-комментарии, /* ... *\/ (с поддержкой вложенности) и package/import-строки. */
    private String removeCommentsAndPackage(String code) {
        StringBuilder out = new StringBuilder(code.length());
        int i = 0, n = code.length();
        int depth = 0;

        while (i < n) {
            char c = code.charAt(i);

            if (c == '/' && i + 1 < n && code.charAt(i + 1) == '*') {
                depth++;
                i += 2;
                continue;
            }
            if (depth > 0) {
                if (c == '*' && i + 1 < n && code.charAt(i + 1) == '/') {
                    depth--;
                    i += 2;
                } else {
                    i++;
                }
                continue;
            }

            if (c == '/' && i + 1 < n && code.charAt(i + 1) == '/') {
                while (i < n && code.charAt(i) != '\n') i++;
                continue;
            }

            out.append(c);
            i++;
        }

        String[] lines = out.toString().split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (String ln : lines) {
            String trimmed = ln.trim();
            if (trimmed.startsWith("package ") || trimmed.startsWith("import ")) {
                sb.append("\n");
            } else {
                sb.append(ln).append('\n');
            }
        }
        return sb.toString();
    }

    /** Маскируем содержимое строковых и символьных литералов пробелами (включая """ ... """). */
    private String maskLiterals(String code) {
        StringBuilder sb = new StringBuilder(code);
        int n = sb.length();
        int i = 0;
        while (i < n) {
            char c = sb.charAt(i);

            // --- Raw string """ ... """ ---
            if (c == '"' && i + 2 < n && sb.charAt(i + 1) == '"' && sb.charAt(i + 2) == '"') {
                int j = i + 3;
                while (j + 2 < n && !(sb.charAt(j) == '"' && sb.charAt(j + 1) == '"' && sb.charAt(j + 2) == '"')) {
                    if (sb.charAt(j) != '\n') sb.setCharAt(j, ' ');
                    j++;
                }
                for (int k = i; k < Math.min(j + 3, n); k++) {
                    if (sb.charAt(k) != '\n') sb.setCharAt(k, ' ');
                }
                i = j + 3;
                continue;
            }

            // --- Обычная строка " ... " ---
            if (c == '"') {
                int j = i + 1;
                while (j < n) {
                    char cj = sb.charAt(j);
                    if (cj == '\\' && j + 1 < n) { sb.setCharAt(j, ' '); sb.setCharAt(j + 1, ' '); j += 2; continue; }
                    if (cj == '"') break;
                    if (cj != '\n') sb.setCharAt(j, ' ');
                    j++;
                }
                i = j + 1;
                continue;
            }

            // --- Символьный литерал ' ... ' ---
            if (c == '\'') {
                int j = i + 1;
                while (j < n) {
                    char cj = sb.charAt(j);
                    if (cj == '\\' && j + 1 < n) { sb.setCharAt(j, ' '); sb.setCharAt(j + 1, ' '); j += 2; continue; }
                    if (cj == '\'') break;
                    sb.setCharAt(j, ' ');
                    j++;
                }
                i = j + 1;
                continue;
            }

            i++;
        }
        return sb.toString();
    }

    /** Пользовательские типы: class Foo, interface Bar, object Baz, enum class Qux. */
    private void collectUserTypes(String code) {
        Pattern p = Pattern.compile(
                "\\b(?:data\\s+|sealed\\s+|open\\s+|abstract\\s+|internal\\s+|private\\s+|public\\s+|protected\\s+|annotation\\s+|enum\\s+)*" +
                        "(?:class|interface|object)\\s+([A-Za-z_]\\w*)");
        Matcher m = p.matcher(code);
        while (m.find()) userTypes.add(m.group(1));
    }

    /** Имена функций: идентификатор перед '(' после fun, либо как вызов — name(...) */
    private void collectFunctionNames(String maskedCode) {
        Pattern p1 = Pattern.compile("\\bfun\\s+(?:<[^>]*>\\s*)?(?:[A-Za-z_]\\w*\\.)?([A-Za-z_]\\w*)\\s*\\(");
        Matcher m1 = p1.matcher(maskedCode);
        while (m1.find()) {
            String name = m1.group(1);
            if (!SKIP_KEYWORDS.contains(name)) functionNames.add(name);
        }
        Pattern p2 = Pattern.compile("\\b([A-Za-z_]\\w*)\\s*\\(");
        Matcher m2 = p2.matcher(maskedCode);
        while (m2.find()) {
            String name = m2.group(1);
            if (COMPOUND_STARTERS.contains(name)) continue;
            if (COMPOUND_PART_KEYWORDS.contains(name)) continue;
            if (KEYWORD_OPERATORS.contains(name)) continue;
            if (SKIP_KEYWORDS.contains(name)) continue;
            if (userTypes.contains(name)) continue;
            functionNames.add(name);
        }
    }

    /** Извлекаем тела функций: fun name(...): Type { ... } (также fun с = expr) */
    private List<String> extractFunctionBodies(String code) {
        List<String> result = new ArrayList<>();
        Pattern p = Pattern.compile("\\bfun\\b[^\\n{;=]*?\\(", Pattern.MULTILINE);
        Matcher m = p.matcher(code);

        List<int[]> used = new ArrayList<>();
        while (m.find()) {
            int parenOpen = code.indexOf('(', m.start());
            if (parenOpen < 0) continue;
            int parenClose = findMatchingDelim(code, parenOpen, '(', ')');
            if (parenClose < 0) continue;

            int k = parenClose + 1;
            while (k < code.length()) {
                char c = code.charAt(k);
                if (c == '{' || c == '=') break;
                if (c == ';') { k = -1; break; }
                k++;
            }
            if (k < 0 || k >= code.length()) continue;

            if (code.charAt(k) == '{') {
                int braceEnd = findMatchingDelim(code, k, '{', '}');
                if (braceEnd > k) {
                    boolean skip = false;
                    for (int[] r : used) {
                        if (k >= r[0] && k <= r[1]) { skip = true; break; }
                    }
                    if (skip) continue;
                    used.add(new int[]{k, braceEnd});
                    result.add(code.substring(k + 1, braceEnd));
                }
            } else {
                int end = code.indexOf('\n', k);
                if (end < 0) end = code.length();
                result.add(code.substring(k + 1, end));
            }
        }
        return result;
    }

    private int findMatchingDelim(String code, int openPos, char open, char close) {
        int depth = 0;
        for (int i = openPos; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c == open) depth++;
            else if (c == close) {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    // ================== Токенизация ==================

    private List<Token> tokenize(String code) {
        List<Token> tokens = new ArrayList<>();
        int i = 0, n = code.length();
        while (i < n) {
            char c = code.charAt(i);

            if (Character.isWhitespace(c)) { i++; continue; }

            // --- Raw string ---
            if (c == '"' && i + 2 < n && code.charAt(i + 1) == '"' && code.charAt(i + 2) == '"') {
                int start = i;
                i += 3;
                while (i + 2 < n && !(code.charAt(i) == '"' && code.charAt(i + 1) == '"' && code.charAt(i + 2) == '"')) i++;
                i = Math.min(i + 3, n);
                tokens.add(new Token(TokenType.StringLiteral, code.substring(start, i)));
                continue;
            }

            // --- Обычная строка ---
            if (c == '"') {
                int start = i; i++;
                while (i < n && code.charAt(i) != '"') {
                    if (code.charAt(i) == '\\' && i + 1 < n) i++;
                    i++;
                }
                if (i < n) i++;
                tokens.add(new Token(TokenType.StringLiteral, code.substring(start, i)));
                continue;
            }

            // --- Символьный литерал ---
            if (c == '\'') {
                int start = i; i++;
                while (i < n && code.charAt(i) != '\'') {
                    if (code.charAt(i) == '\\' && i + 1 < n) i++;
                    i++;
                }
                if (i < n) i++;
                tokens.add(new Token(TokenType.CharLiteral, code.substring(start, i)));
                continue;
            }

            // --- Числа ---
            if (Character.isDigit(c)) {
                int start = i;
                while (i < n) {
                    char ci = code.charAt(i);
                    if (Character.isLetterOrDigit(ci) || ci == '.' || ci == '_') i++;
                    else break;
                }
                tokens.add(new Token(TokenType.Number, code.substring(start, i)));
                continue;
            }

            // --- Идентификатор ---
            if (Character.isLetter(c) || c == '_') {
                int start = i;
                while (i < n && (Character.isLetterOrDigit(code.charAt(i)) || code.charAt(i) == '_')) i++;
                tokens.add(new Token(TokenType.Identifier, code.substring(start, i)));
                continue;
            }

            // --- Многосимвольные операторы ---
            boolean matched = false;
            for (String op : MULTI_CHAR_OPS) {
                if (i + op.length() <= n && code.startsWith(op, i)) {
                    tokens.add(new Token(TokenType.Operator, op));
                    i += op.length();
                    matched = true;
                    break;
                }
            }
            if (matched) continue;

            tokens.add(new Token(TokenType.Operator, String.valueOf(c)));
            i++;
        }
        return tokens;
    }

    // ================== Разбор токенов ==================

    private void analyzeTokens(List<Token> tokens) {
        int i = 0;
        while (i < tokens.size()) {
            Token t = tokens.get(i);

            // --- Управляющие конструкции ---
            if (t.type == TokenType.Identifier) {
                switch (t.value) {
                    case "if":
                        if (hasElseAfter(tokens, i)) addOperator("if ... else");
                        else addOperator("if");
                        i++; continue;
                    case "when":
                        addOperator("when ... else");
                        i++; continue;
                    case "for":
                        addOperator("for()");
                        i++; continue;
                    case "while":
                        addOperator("while()");
                        i++; continue;
                    case "do":
                        addOperator("do ... while()");
                        i++; continue;
                    case "try":
                        addOperator("try ... catch ... finally");
                        i++; continue;
                }

                if (COMPOUND_PART_KEYWORDS.contains(t.value)) { i++; continue; }
                if (KEYWORD_OPERATORS.contains(t.value)) { addOperator(t.value); i++; continue; }

                // Типы/модификаторы/служебные — пропускаем
                if (SKIP_KEYWORDS.contains(t.value)) { i++; continue; }

                // НОВОЕ: идентификатор + '(' (или '{' для trailing lambda) => оператор вызова
                if (isFunctionCall(tokens, i) || isTrailingLambdaCall(tokens, i)) {
                    addOperator(t.value + "()");
                    i++;
                    // Если это вызов с круглыми скобками — пропускаем саму '(', чтобы не удваивать "()"
                    if (i < tokens.size() && isOp(tokens.get(i), "(")) {
                        i++; // пропускаем '('
                    }
                    continue;
                }

                // Иначе — обычная переменная/поле → операнд
                handleIdentifier(tokens, i);
                i++;
                continue;
            }

            // --- Литералы (операнды) ---
            if (t.type == TokenType.Number ||
                    t.type == TokenType.StringLiteral ||
                    t.type == TokenType.CharLiteral) {
                addOperand(t.value);
                i++;
                continue;
            }

            // --- Операторы ---
            if (t.type == TokenType.Operator) {
                handleOperatorToken(tokens, i);
                i++;
                continue;
            }

            i++;
        }
    }

    /**
     * Возвращает true, если сразу за идентификатором (с учётом опциональных
     * generic-параметров <...>) идёт открывающая круглая скобка.
     */
    private boolean isFunctionCall(List<Token> tokens, int idx) {
        int j = idx + 1;
        if (j >= tokens.size()) return false;

        // Пропускаем generic-параметры: < ... >
        if (isOp(tokens.get(j), "<")) {
            int depth = 0;
            while (j < tokens.size()) {
                Token tk = tokens.get(j);
                if (tk.type == TokenType.Operator && tk.value.equals("<")) depth++;
                else if (tk.type == TokenType.Operator && tk.value.equals(">")) {
                    depth--;
                    if (depth == 0) { j++; break; }
                } else if (tk.type == TokenType.Operator && tk.value.equals(";")) {
                    return false;
                }
                j++;
            }
        }

        return j < tokens.size() && isOp(tokens.get(j), "(");
    }

    /** Случай вызова без круглых скобок: foo { ... } (trailing lambda). */
    private boolean isTrailingLambdaCall(List<Token> tokens, int idx) {
        int j = idx + 1;
        return j < tokens.size() && isOp(tokens.get(j), "{");
    }

    /** Проверяет, идёт ли после if ... else. */
    private boolean hasElseAfter(List<Token> tokens, int ifIdx) {
        int openParen = ifIdx + 1;
        if (openParen >= tokens.size()) return false;
        if (!isOp(tokens.get(openParen), "(")) return false;
        int closeParen = findCloseParen(tokens, openParen);
        if (closeParen < 0) return false;

        int j = closeParen + 1;
        if (j >= tokens.size()) return false;

        if (isOp(tokens.get(j), "{")) {
            int end = findCloseBraceTokens(tokens, j);
            if (end < 0) return false;
            j = end + 1;
        } else {
            int depthBrace = 0, depthParen = 0;
            while (j < tokens.size()) {
                Token tk = tokens.get(j);
                if (tk.type == TokenType.Operator) {
                    switch (tk.value) {
                        case "{": depthBrace++; break;
                        case "}": if (depthBrace == 0) { j = -1; } else depthBrace--; break;
                        case "(": depthParen++; break;
                        case ")": depthParen--; break;
                        case ";": if (depthBrace == 0 && depthParen == 0) { j++; } break;
                    }
                    if (j < 0) break;
                }
                if (j >= 0 && j < tokens.size()) {
                    Token c = tokens.get(j);
                    if (c.type == TokenType.Operator && c.value.equals(";") && depthBrace == 0 && depthParen == 0) {
                        j++;
                        break;
                    }
                }
                j++;
            }
        }

        return j >= 0 && j < tokens.size()
                && tokens.get(j).type == TokenType.Identifier
                && tokens.get(j).value.equals("else");
    }

    private boolean isOp(Token t, String v) {
        return t.type == TokenType.Operator && t.value.equals(v);
    }

    private int findCloseParen(List<Token> tokens, int openIdx) {
        if (openIdx >= tokens.size() || !isOp(tokens.get(openIdx), "(")) return -1;
        int depth = 0;
        for (int i = openIdx; i < tokens.size(); i++) {
            if (isOp(tokens.get(i), "(")) depth++;
            else if (isOp(tokens.get(i), ")")) { depth--; if (depth == 0) return i; }
        }
        return -1;
    }

    private int findCloseBraceTokens(List<Token> tokens, int openIdx) {
        if (openIdx >= tokens.size() || !isOp(tokens.get(openIdx), "{")) return -1;
        int depth = 0;
        for (int i = openIdx; i < tokens.size(); i++) {
            if (isOp(tokens.get(i), "{")) depth++;
            else if (isOp(tokens.get(i), "}")) { depth--; if (depth == 0) return i; }
        }
        return -1;
    }

    // ================== Обработка операторов ==================

    private void handleOperatorToken(List<Token> tokens, int idx) {
        String v = tokens.get(idx).value;

        if (v.equals(")") || v.equals("}") || v.equals("]")) return;

        if (v.equals("(")) {
            if (isTypeCast(tokens, idx)) addOperator("(type)");
            else addOperator("()");
            return;
        }
        if (v.equals("{")) { addOperator("{}"); return; }
        if (v.equals("[")) { addOperator("[]"); return; }

        if (v.equals("?:")) { addOperator("?:"); return; }
        if (v.equals(":"))  { addOperator(":");  return; }

        if (v.equals("-")) {
            addOperator(isUnary(tokens, idx) ? "-(unary)" : "-");
            return;
        }
        if (v.equals("+")) {
            addOperator(isUnary(tokens, idx) ? "+(unary)" : "+");
            return;
        }
        if (v.equals("!")) { addOperator("!"); return; }

        if (v.equals(",")) { addOperator(","); return; }
        if (v.equals(";")) { addOperator(";"); return; }
        if (v.equals(".")) { addOperator("."); return; }
        if (v.equals("..")) { addOperator(".."); return; }
        if (v.equals("..<")) { addOperator("..<"); return; }
        if (v.equals("->")) { addOperator("->"); return; }
        if (v.equals("::")) { addOperator("::"); return; }
        if (v.equals("?.")) { addOperator("?."); return; }
        if (v.equals("!!")) { addOperator("!!"); return; }

        addOperator(v);
    }

    /** Унарный +/- : если предыдущий значимый токен — оператор начала выражения/запятая/скобка/ключевое слово. */
    private boolean isUnary(List<Token> tokens, int idx) {
        if (idx == 0) return true;
        Token prev = tokens.get(idx - 1);
        if (prev.type == TokenType.Operator) {
            String pv = prev.value;
            if (pv.equals(")") || pv.equals("]") || pv.equals("}")) return false;
            if (pv.equals("++") || pv.equals("--")) return false;
            return true;
        }
        if (prev.type == TokenType.Identifier) {
            String pv = prev.value;
            if (pv.equals("return") || pv.equals("throw") || pv.equals("in")
                    || pv.equals("is") || pv.equals("as")) return true;
            return false;
        }
        return false;
    }

    /** Приведение типа: (Int) expr, (String?) s */
    private boolean isTypeCast(List<Token> tokens, int openIdx) {
        if (openIdx + 2 >= tokens.size()) return false;
        int k = openIdx + 1;

        Token first = tokens.get(k);
        if (first.type != TokenType.Identifier) return false;

        String typeName = first.value;
        boolean known = TYPE_KEYWORDS.contains(typeName)
                || userTypes.contains(typeName)
                || (!typeName.isEmpty() && Character.isUpperCase(typeName.charAt(0)));
        if (!known) return false;
        k++;

        if (k < tokens.size() && isOp(tokens.get(k), "?")) k++;

        return k < tokens.size() && isOp(tokens.get(k), ")");
    }

    /** Идентификатор-операнд: переменная, поле. Имена вызовов сюда НЕ попадают. */
    private void handleIdentifier(List<Token> tokens, int idx) {
        String name = tokens.get(idx).value;

        if (SKIP_KEYWORDS.contains(name)) return;
        if (KEYWORD_OPERATORS.contains(name)) { addOperator(name); return; }
        if (COMPOUND_STARTERS.contains(name)) return;

        addOperand(name);
    }

    // ================== Накопление и вывод ==================

    private void addOperator(String name) {
        operators.merge(name, 1, Integer::sum);
    }

    private void addOperand(String name) {
        operands.merge(name, 1, Integer::sum);
    }

    private void displayResults() {
        List<Map.Entry<String, Integer>> opList = new ArrayList<>(operators.entrySet());
        opList.sort((a, b) -> {
            int c = Integer.compare(b.getValue(), a.getValue());
            return c != 0 ? c : a.getKey().compareTo(b.getKey());
        });
        List<Map.Entry<String, Integer>> odList = new ArrayList<>(operands.entrySet());
        odList.sort((a, b) -> {
            int c = Integer.compare(b.getValue(), a.getValue());
            return c != 0 ? c : a.getKey().compareTo(b.getKey());
        });

        int n1 = opList.size();
        int N1 = opList.stream().mapToInt(Map.Entry::getValue).sum();
        int n2 = odList.size();
        int N2 = odList.stream().mapToInt(Map.Entry::getValue).sum();

        progDict = n1 + n2;
        progLen = N1 + N2;
        progVolume = progDict > 1 ? progLen * (Math.log(progDict) / Math.log(2)) : 0;

        operatorsModel.setRowCount(0);
        for (Map.Entry<String, Integer> e : opList)
            operatorsModel.addRow(new Object[]{e.getKey(), e.getValue()});
        operatorsModel.addRow(new Object[]{"ИТОГО  η1 = " + n1, N1});

        operandsModel.setRowCount(0);
        for (Map.Entry<String, Integer> e : odList)
            operandsModel.addRow(new Object[]{e.getKey(), e.getValue()});
        operandsModel.addRow(new Object[]{"ИТОГО  η2 = " + n2, N2});

        StringBuilder sb = new StringBuilder();
        sb.append("БАЗОВЫЕ МЕТРИКИ ХОЛСТЕДА\n");
        sb.append(String.format("  η1  — словарь операторов      = %d%n", n1));
        sb.append(String.format("  η2  — словарь операндов       = %d%n", n2));
        sb.append(String.format("  N1  — всего операторов        = %d%n", N1));
        sb.append(String.format("  N2  — всего операндов         = %d%n", N2));
        sb.append('\n');
        sb.append("РАСШИРЕННЫЕ МЕТРИКИ\n");
        sb.append(String.format("  η = η1 + η2                   = %d%n", progDict));
        sb.append(String.format("  N = N1 + N2                   = %d%n", progLen));
        sb.append(String.format("  V = N · log2(η)               = %.2f%n", progVolume));

        metricsArea.setText(sb.toString());
    }

    // ================== Точка входа ==================

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Main frame = new Main();
            frame.setVisible(true);

            File f = new File("..\\new_parser\\src\\test.kt");
            if (f.exists()) frame.analyzeFile(f.getAbsolutePath());
        });
    }
}