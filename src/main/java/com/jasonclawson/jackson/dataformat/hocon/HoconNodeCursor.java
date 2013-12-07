package com.jasonclawson.jackson.dataformat.hocon;

import java.util.Iterator;
import java.util.Map;

import com.fasterxml.jackson.core.JsonStreamContext;
import com.fasterxml.jackson.core.JsonToken;
import com.typesafe.config.ConfigList;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueType;

public abstract class HoconNodeCursor extends JsonStreamContext {

	protected final HoconNodeCursor _parent;
	protected String _currentName;

	public HoconNodeCursor(int contextType, HoconNodeCursor p) {
		_type = contextType;
		_index = -1;
		_parent = p;
	}

	@Override
	public HoconNodeCursor getParent() {
		return _parent;
	}

	@Override
	public String getCurrentName() {
		return _currentName;
	}
	
	public void overrideCurrentName(String name) {
        _currentName = name;
    }
	
	public abstract JsonToken nextToken();
    public abstract JsonToken nextValue();
    public abstract JsonToken endToken();

    public abstract ConfigValue currentNode();
    public abstract boolean currentHasChildren();
    
    protected static boolean isArray(ConfigValue value) {
    	return value.valueType() == ConfigValueType.LIST;
    }
    
    protected static boolean isObject(ConfigValue value) {
    	return value.valueType() == ConfigValueType.OBJECT;
    }
    
    protected static JsonToken asJsonToken(ConfigValue value) {
		return HoconTreeTraversingParser.asJsonToken(value);
	}
    
    /**
     * Method called to create a new context for iterating all
     * contents of the current structured value (JSON array or object)
     */
    public final HoconNodeCursor iterateChildren() {
    	ConfigValue n = currentNode();
        if (n == null) throw new IllegalStateException("No current node");
        if (isArray(n)) { // false since we have already returned START_ARRAY
            return new Array(n, this);
        }
        if (isObject(n)) {
            return new Object(n, this);
        }
        throw new IllegalStateException("Current node of type "+n.getClass().getName());
    }
    
    protected final static class RootValue
    extends HoconNodeCursor
{
    	 protected ConfigValue _node;

         protected boolean _done = false;

         public RootValue(ConfigValue n, HoconNodeCursor p) {
             super(JsonStreamContext.TYPE_ROOT, p);
             _node = n;
         }
         
         @Override
         public void overrideCurrentName(String name) {
             
         }
         
         @Override
         public JsonToken nextToken() {
             if (!_done) {
                 _done = true;
                 return asJsonToken(_node);
             }
             _node = null;
             return null;
         }
         
         @Override
         public JsonToken nextValue() { return nextToken(); }
         @Override
         public JsonToken endToken() { return null; }
         @Override
         public ConfigValue currentNode() { return _node; }
         @Override
         public boolean currentHasChildren() { return false; }
    	
}
    
    
    
    
    /**
     * Cursor used for traversing non-empty JSON Array nodes
     */
    protected final static class Array
        extends HoconNodeCursor
    {
        protected Iterator<ConfigValue> _contents;

        protected ConfigValue _currentNode;

        public Array(ConfigValue n, HoconNodeCursor p) {
            super(JsonStreamContext.TYPE_ARRAY, p);
            _contents = ((ConfigList)n).iterator();
        }

        @Override
        public JsonToken nextToken()
        {
            if (!_contents.hasNext()) {
                _currentNode = null;
                return null;
            }
            _currentNode = _contents.next();
            return asJsonToken(_currentNode);
        }

        @Override
        public JsonToken nextValue() { return nextToken(); }
        @Override
        public JsonToken endToken() { return JsonToken.END_ARRAY; }

        @Override
        public ConfigValue currentNode() { return _currentNode; }
        @Override
        public boolean currentHasChildren() {
        	if(currentNode() instanceof ConfigList) {
        		return !((ConfigList)currentNode()).isEmpty();
        	} else if (currentNode() instanceof ConfigObject) {
        		return !((ConfigObject)currentNode()).isEmpty();
        	} else {
        		return false;
        	}
        }
    }

    /**
     * Cursor used for traversing non-empty JSON Object nodes
     */
    protected final static class Object
        extends HoconNodeCursor
    {
        protected Iterator<Map.Entry<String, ConfigValue>> _contents;
        protected Map.Entry<String, ConfigValue> _current;

        protected boolean _needEntry;
        
        public Object(ConfigValue n, HoconNodeCursor p)
        {
            super(JsonStreamContext.TYPE_OBJECT, p);
            _contents = ((ConfigObject) n).entrySet().iterator();
            _needEntry = true;
        }

        @Override
        public JsonToken nextToken()
        {
            // Need a new entry?
            if (_needEntry) {
                if (!_contents.hasNext()) {
                    _currentName = null;
                    _current = null;
                    return null;
                }
                _needEntry = false;
                _current = _contents.next();
                _currentName = (_current == null) ? null : _current.getKey();
                return JsonToken.FIELD_NAME;
            }
            _needEntry = true;
            return asJsonToken(_current.getValue());
        }

        @Override
        public JsonToken nextValue()
        {
            JsonToken t = nextToken();
            if (t == JsonToken.FIELD_NAME) {
                t = nextToken();
            }
            return t;
        }

        @Override
        public JsonToken endToken() { return JsonToken.END_OBJECT; }

        @Override
        public ConfigValue currentNode() {
            return (_current == null) ? null : _current.getValue();
        }
        @Override
        public boolean currentHasChildren() {
        	if(currentNode() instanceof ConfigList) {
        		return !((ConfigList)currentNode()).isEmpty();
        	} else if (currentNode() instanceof ConfigObject) {
        		return !((ConfigObject)currentNode()).isEmpty();
        	} else {
        		return false;
        	}
        }
    }
    
    
    

}