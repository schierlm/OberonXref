package oberonxref;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

public class Scope {
	private Scope parent;
	private final String linkPrefix;
	private Scope publicScope = null;
	private final Map<String, Type> identifierTypes = new HashMap<>();
	private final Map<String, Scope> identifierScopes = new HashMap<>();
	private final Map<String, Scope> unresolvedPointerTypes = new HashMap<>();

	public Scope(Scope parent, String linkPrefix, Scope prefixScope) {
		this.parent = parent;
		this.linkPrefix = (prefixScope != null ? prefixScope.linkPrefix : "") + linkPrefix;
	}

	public Scope getParent() {
		return parent;
	}

	public Scope getPublicScope() {
		return publicScope;
	}

	public void setPublicScope(Scope publicScope) {
		this.publicScope = publicScope;
	}

	public void putIdentifier(String ident, Type type, Scope scope) {
		if (identifierTypes.containsKey(ident))
			throw new IllegalArgumentException("Duplicate identifier " + ident);
		identifierTypes.put(ident, type);
		if (scope != null)
			identifierScopes.put(ident, scope);
		if (type == Type.TYPE && unresolvedPointerTypes.containsKey(ident)) {
			Scope pointerScope = unresolvedPointerTypes.remove(ident);
			pointerScope.parent = scope;
			pointerScope.identifierScopes.put("^", scope);
		}
	}

	public Scope makePointerScope(String pointerOf) {
		Scope pointerScope = new Scope(null, pointerOf + ".", this);
		pointerScope.putIdentifier("^", Type.VARIABLE, pointerScope);
		if (identifierTypes.containsKey(pointerOf)) {
			Scope identScope = getIdentifierScope(pointerOf);
			pointerScope.parent = identScope;
			pointerScope.identifierScopes.put("^", identScope);
		} else {
			unresolvedPointerTypes.put(pointerOf, pointerScope);
		}
		return pointerScope;
	}

	public boolean isIdentifierDefined(String ident) {
		return identifierTypes.containsKey(ident) || (parent != null && parent.isIdentifierDefined(ident));
	}

	public Type getIdentifierType(String ident) {
		if (identifierTypes.containsKey(ident))
			return identifierTypes.get(ident);
		if (parent != null)
			return parent.getIdentifierType(ident);
		throw new NoSuchElementException(ident);
	}

	public Scope getIdentifierScope(String ident) {
		if (identifierTypes.containsKey(ident))
			return identifierScopes.get(ident);
		if (parent != null)
			return parent.getIdentifierScope(ident);
		throw new NoSuchElementException(ident);
	}

	public String getIdentifierLink(String ident) {
		String link = getIdentifierLink0(ident);
		if (link.contains("@@"))
			throw new RuntimeException("Invalid link: " + link);
		return link;
	}

	private String getIdentifierLink0(String ident) {
		if (identifierTypes.containsKey(ident))
			return linkPrefix + ident;
		if (parent != null)
			return parent.getIdentifierLink(ident);
		throw new NoSuchElementException(ident);
	}

	public static enum Type {
		CONSTANT, VARIABLE, TYPE, IMPORTED_MODULE, PROCEDURE
	}
}
