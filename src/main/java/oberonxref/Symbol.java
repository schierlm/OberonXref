package oberonxref;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class Symbol {
	private final SymbolType type;
	private final String text;
	private String link = null;

	public Symbol(SymbolType type, String text) {
		this.type = type;
		this.text = text;
	}

	public SymbolType getType() {
		return type;
	}

	public String getText() {
		return text;
	}

	public String getLink() {
		return link;
	}

	public void setLink(String link) {
		this.link = link;
	}
	

	public String getCSS() {
		return type.category.css;
	}
	
	@Override
	public String toString() {
		return "Symbol »"+text+"«["+type+"]";
	}
	

	public boolean hasTypeOneOf(SymbolType... types) {
		return Arrays.asList(types).contains(type);
	}

	public static Map<String,SymbolType> KEYWORD_MAP = new HashMap<>();
	
	public static enum SymbolType {

		CONST(SymbolCategory.CONSTANT),

		IDENTIFIER(SymbolCategory.IDENTIFIER),

		OP_AND(SymbolCategory.OPERATOR),
		OP_ARROW(SymbolCategory.OPERATOR),
		OP_BAR(SymbolCategory.OPERATOR),
		OP_BECOMES(SymbolCategory.OPERATOR),
		OP_COLON(SymbolCategory.OPERATOR),
		OP_COMMA(SymbolCategory.OPERATOR),
		OP_EQL(SymbolCategory.OPERATOR),
		OP_GEQ(SymbolCategory.OPERATOR),
		OP_GTR(SymbolCategory.OPERATOR),
		OP_LBRACE(SymbolCategory.OPERATOR),
		OP_LBRAK(SymbolCategory.OPERATOR),
		OP_LEQ(SymbolCategory.OPERATOR),
		OP_LPAREN(SymbolCategory.OPERATOR),
		OP_LSS(SymbolCategory.OPERATOR),
		OP_MINUS(SymbolCategory.OPERATOR),
		OP_NEQ(SymbolCategory.OPERATOR),
		OP_NOT(SymbolCategory.OPERATOR),
		OP_PERIOD(SymbolCategory.OPERATOR),
		OP_PLUS(SymbolCategory.OPERATOR),
		OP_RBRACE(SymbolCategory.OPERATOR),
		OP_RBRAK(SymbolCategory.OPERATOR),
		OP_RDIV(SymbolCategory.OPERATOR),
		OP_RPAREN(SymbolCategory.OPERATOR),
		OP_SEMICOLON(SymbolCategory.OPERATOR),
		OP_TIMES(SymbolCategory.OPERATOR),
		OP_UPTO(SymbolCategory.OPERATOR),

		KEYWORD_ARRAY(SymbolCategory.KEYWORD),
		KEYWORD_BEGIN(SymbolCategory.KEYWORD),
		KEYWORD_BY(SymbolCategory.KEYWORD),
		KEYWORD_CASE(SymbolCategory.KEYWORD),
		KEYWORD_CONST(SymbolCategory.KEYWORD),
		KEYWORD_DIV(SymbolCategory.KEYWORD),
		KEYWORD_DO(SymbolCategory.KEYWORD),
		KEYWORD_ELSE(SymbolCategory.KEYWORD),
		KEYWORD_ELSIF(SymbolCategory.KEYWORD),
		KEYWORD_END(SymbolCategory.KEYWORD),
		KEYWORD_FALSE(SymbolCategory.KEYWORD),
		KEYWORD_FOR(SymbolCategory.KEYWORD),
		KEYWORD_IF(SymbolCategory.KEYWORD),
		KEYWORD_IMPORT(SymbolCategory.KEYWORD),
		KEYWORD_IN(SymbolCategory.KEYWORD),
		KEYWORD_IS(SymbolCategory.KEYWORD),
		KEYWORD_MOD(SymbolCategory.KEYWORD),
		KEYWORD_MODULE(SymbolCategory.KEYWORD),
		KEYWORD_NIL(SymbolCategory.KEYWORD),
		KEYWORD_OF(SymbolCategory.KEYWORD),
		KEYWORD_OR(SymbolCategory.KEYWORD),
		KEYWORD_POINTER(SymbolCategory.KEYWORD),
		KEYWORD_PROCEDURE(SymbolCategory.KEYWORD),
		KEYWORD_RECORD(SymbolCategory.KEYWORD),
		KEYWORD_REPEAT(SymbolCategory.KEYWORD),
		KEYWORD_RETURN(SymbolCategory.KEYWORD),
		KEYWORD_THEN(SymbolCategory.KEYWORD),
		KEYWORD_TO(SymbolCategory.KEYWORD),
		KEYWORD_TRUE(SymbolCategory.KEYWORD),
		KEYWORD_TYPE(SymbolCategory.KEYWORD),
		KEYWORD_UNTIL(SymbolCategory.KEYWORD),
		KEYWORD_VAR(SymbolCategory.KEYWORD),
		KEYWORD_WHILE(SymbolCategory.KEYWORD),

		SKIP_COMMENT(SymbolCategory.COMMENT),
		SKIP_WHITESPACE(SymbolCategory.WHITESPACE),
		SKIP_LINEBREAK(SymbolCategory.WHITESPACE);

		public final SymbolCategory category;

		private SymbolType(SymbolCategory category) {
			this.category = category;
		}
		
		static {
			for(SymbolType st : values()) {
				if (st.name().startsWith("KEYWORD_") && st.category == SymbolCategory.KEYWORD) 
					KEYWORD_MAP.put(st.name().substring(8), st);
			}
		}
	}

	public static enum SymbolCategory {

		KEYWORD("color: #f0f;"),
		CONSTANT("color: #666;"),
		COMMENT("color: #0a0;"),
		IDENTIFIER("color: #00f;"),
		OPERATOR(""),
		WHITESPACE("");

		public final String css;

		private SymbolCategory(String css) {
			this.css = css;
		}
	}
}
