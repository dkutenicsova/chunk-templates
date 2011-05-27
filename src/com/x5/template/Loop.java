package com.x5.template;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.x5.util.DataCapsuleTable;
import com.x5.util.TableData;

public class Loop extends BlockTag
{
    private TableData data;
    private Chunk chunk;
    private String rowTemplate;
    private String emptyTemplate;
    private Map<String,String> options;

    public static void main(String[] args)
    {
        String loopTest =
            "{~.loop data=\"~mydata\" template=\"#test_row\" no_data=\"#test_empty\"}";
        // test that the parser is not in and endless loop.
        Loop loop = new Loop(loopTest,null);
        System.out.println("row_tpl="+loop.rowTemplate);
        System.out.println("empty_tpl="+loop.emptyTemplate);
    }

    public static String expandLoop(String params, Chunk ch)
    throws BlockTagException
    {
        Loop loop = new Loop(params, ch);
        return loop._cookLoop();
    }

    public Loop(String params, Chunk ch)
    {
        this.chunk = ch;
        parseParams(params);
    }

    private void parseParams(String params)
    {
        if (params == null) return;

        if (params.startsWith(".loop(")) {
            parseFnParams(params);
        } else if (params.matches("\\.loop [^\" ]+ .*")) {
            parseEZParams(params);
        } else {
            parseAttributes(params);
        }
    }
    
    // {^loop in ~data}...{^/loop} (or {^loop in ~data as x}...)
    private void parseEZParams(String paramString)
    {
        String[] params = paramString.split(" ");
        String dataVar = params[2];
        fetchData(dataVar);
        
        this.options = _parseAttributes(paramString);
        
        if (params.length > 3) {
            if (params[3].equals("as")) {
                if (options == null) options = new HashMap<String,String>();
                options.put("name",params[4]);
            }
        }
    }

    // ^loop(~data[...range...],#rowTemplate,#emptyTemplate)
    private void parseFnParams(String params)
    {
        int endOfParams = params.length();
        if (params.endsWith(")")) endOfParams--;
        params = params.substring(".loop(".length(),endOfParams);
        String[] args = params.split(",");
        if (args != null && args.length >= 2) {
            String dataVar = args[0];
            fetchData(dataVar);

            this.rowTemplate = args[1];
            if (args.length > 2) {
                this.emptyTemplate = args[2];
            } else {
                this.emptyTemplate = null;
            }
        }
    }
    
    private Map<String,String> _parseAttributes(String params)
    {
        // find and save all xyz="abc" style attributes
        Pattern p = Pattern.compile(" ([a-zA-Z0-9_-]+)=(\"([^\"]*)\"|'([^\']*)')");
        Matcher m = p.matcher(params);
        HashMap<String,String> opts = null;
        while (m.find()) {
            m.group(0); // need to do this for subsequent number to be correct?
            String paramName = m.group(1);
            String paramValue = m.group(3);
            if (opts == null) opts = new HashMap<String,String>();
            opts.put(paramName, paramValue);
        }
        return opts;
    }
    
    // ^loop data="~data" template="#..." no_data="#..." range="..." per_page="x" page="x"
    private void parseAttributes(String params)
    {
        Map<String,String> opts = _parseAttributes(params);
        
        if (opts == null) return;
        this.options = opts;
        
        String dataVar = opts.get("data");
        fetchData(dataVar);
        
        this.rowTemplate = opts.get("template");
        this.emptyTemplate = opts.get("no_data");
        
        /*
        String dataVar = getAttribute("data", params);
        fetchData(dataVar);
        this.rowTemplate = getAttribute("template", params);
        this.emptyTemplate = getAttribute("no_data", params);

        // okay, this is heinously inefficient, scanning the whole thing every time for each param
        // esp. optional params which probably won't even be there
        String[] optional = new String[]{"range","divider","trim"}; //... what else?
        for (int i=0; i<optional.length; i++) {
            String param = optional[i];
            String val = getAttribute(param, params);
            if (val != null) registerOption(param, val);

            // really?
            if (param.equals("range") && val == null) {
                // alternate range specification via optional params page and per_page
                String perPage = getAttribute("per_page", params);
                String page = getAttribute("page", params);
                if (perPage != null && page != null) {
                    registerOption("range", page + "*" + perPage);
                }
            }
        }*/
    }

    private void fetchData(String dataVar)
    {
        if (dataVar != null) {
            int rangeMarker = dataVar.indexOf("[");
            if (rangeMarker > 0) {
                int rangeMarker2 = dataVar.indexOf("]",rangeMarker);
                if (rangeMarker2 < 0) rangeMarker2 = dataVar.length();
                String range = dataVar.substring(rangeMarker+1,rangeMarker2);
                dataVar = dataVar.substring(0,rangeMarker);
                registerOption("range",range);
            }
            if (dataVar.charAt(0) == '^') {
                // expand "external" shortcut syntax eg ^wiki becomes ~.wiki
                dataVar = TextFilter.applyRegex(dataVar, "s/^\\^/~./");
            }
            if (dataVar.startsWith("~")) {
                // tag reference (eg, tag assigned to query result table)
                dataVar = dataVar.substring(1);

                if (chunk != null) {
                    Object dataStore = chunk.get(dataVar);
                    if (dataStore instanceof TableData) {
                        this.data = (TableData)dataStore;
                    } else if (dataStore instanceof String) {
                        this.data = InlineTable.parseTable((String)dataStore);
                        registerOption("array_index_tags","FALSE");
                    } else if (dataStore instanceof String[]) {
                    	this.data = new SimpleTable((String[])dataStore);
                    } else if (dataStore instanceof Object[]) {
                    	// assume array of objects that implement DataCapsule
                    	this.data = DataCapsuleTable.extractData((Object[])dataStore);
                        registerOption("array_index_tags","FALSE");
                    }
                }
            } else {
                // template reference
                if (chunk != null) {
                	String tableAsString = chunk.getTemplateSet().fetch(dataVar);
                    this.data = InlineTable.parseTable(tableAsString);
                }
            }
        }
    }

    private void registerOption(String param, String value)
    {
        if (options == null) options = new java.util.HashMap<String,String>();
        options.put(param,value);
    }

    private String _cookLoop()
    throws BlockTagException
    {
    	if (rowTemplate == null) throw new BlockTagException(this);
        return Loop.cookLoop(data, chunk, rowTemplate, emptyTemplate, options, false);
    }

    public static String cookLoop(TableData data, Chunk context,
    		String rowTemplate, String emptyTemplate,
    		Map<String,String> opt, boolean isBlock)
    {
        if (data == null || !data.hasNext()) {
            if (emptyTemplate == null) {
                if (isBlock) {
                    return "[Loop error: Empty Table - please supply ^onEmpty section in ^loop block]";
                } else {
                    return "[Loop Error: Empty Table - please specify no_data template parameter in ^loop tag]";
                }
            } else if (emptyTemplate.length() == 0) {
            	return "";
            } else {
                if (isBlock) {
                    return emptyTemplate;
                } else {
                    return context.getTemplateSet().fetch(emptyTemplate);
                }
            }
        }
        
        String dividerTemplate = null;
        boolean createArrayTags = true;
        
        if (opt != null) {
        	if (opt.containsKey("divider")) {
	        	dividerTemplate = (String)opt.get("divider");
	        	ContentSource templates = context.getTemplateSet();
	        	if (templates.provides(dividerTemplate)) {
	        		dividerTemplate = templates.fetch(dividerTemplate);
	        	}
        	}
        	if (opt.containsKey("array_index_tags")) {
        		createArrayTags = false;
        	}
        }

        ChunkFactory factory = context.getChunkFactory();

        String[] columnLabels = data.getColumnLabels();

        StringBuilder rows = new StringBuilder();
        Chunk rowX;
        if (isBlock || factory == null) {
            rowX = (factory == null) ? new Chunk() : factory.makeChunk();
            rowX.append(rowTemplate);
        } else {
            rowX = factory.makeChunk(rowTemplate);
        }
        int counter = 0;
        while (data.hasNext()) {
            rowX.set("0",counter);
            rowX.set("1",counter+1);
            
            if (dividerTemplate != null && counter > 0) {
            	rows.append( dividerTemplate );
            }

            Map<String,String> record = data.nextRecord();

            String prefix = null;
            if (opt != null && opt.containsKey("name")) {
                String name = opt.get("name");
                prefix = TextFilter.applyRegex(name, "s/[^A-Za-z0-9_-]//g") + ".";
            }
                
            // loop backwards -- in case any headers are identical,
            // this ensures the first such named column will be used
            for (int i=columnLabels.length-1; i>-1; i--) {
                String field = columnLabels[i];
                String value = record.get(field);
                // prefix with eg x. if prefix supplied
                String fieldName = prefix == null ? field : prefix + field;
                rowX.setOrDelete(fieldName, value);
                if (createArrayTags) {
	                rowX.setOrDelete("DATA["+i+"]",value);
                }
            }

            // make sure chunk tags are resolved in context
            rows.append( rowX.toString(context) );

            counter++;
        }

        return rows.toString();
    }
    
    public static String getAttribute(String attr, String toScan)
    {
        if (toScan == null) return null;

        // locate attributes
        int spacePos = toScan.indexOf(' ');

        // no attributes? (no spaces before >)
        if (spacePos < 0) return null;

        // pull out just the attribute definitions
        String attrs = toScan.substring(spacePos+1);

        // find our attribute
        int attrPos = attrs.indexOf(attr);
        if (attrPos < 0) return null;

        // find the equals sign
        int eqPos = attrs.indexOf('=',attrPos + attr.length());

        // find the opening quote
        int begQuotePos = attrs.indexOf('"',eqPos);
        if (begQuotePos < 0) return null;

        // find the closing quote
        int endQuotePos = begQuotePos+1;
        do {
            endQuotePos = attrs.indexOf('"',endQuotePos);
            if (endQuotePos < 0) return null;
            // FIXME this could get tripped up by escaped slash followed by unescaped quote
            if (attrs.charAt(endQuotePos-1) == '\\') {
                // escaped quote, doesn't count -- keep seeking
                endQuotePos++;
            }
        } while (endQuotePos < attrs.length() && attrs.charAt(endQuotePos) != '"');

        if (endQuotePos < attrs.length()) {
            return attrs.substring(begQuotePos+1,endQuotePos);
        } else {
            // never found closing quote
            return null;
        }
    }

    public String cookBlock(String blockBody)
    {
        // split body up into row template and optional empty template
        //  (delimited by {^on_empty} )
        // trim both, unless requested not to.
//        return null;
        boolean isBlock = true;
        
        boolean doTrim = true;
        String trimOpt = (options == null) ? null : options.get("trim");
        if (trimOpt != null && trimOpt.equalsIgnoreCase("false")) {
            doTrim = false;
        }
        
        String delim = "{~.onEmpty}";
        String dividerDelim = "{~.divider}";

        String divider = null;

        // how do we know these aren't inside a nested ^loop block?
        // find any nested blocks and only scan for these markers *outside* the
        // nested sections.
        int[] nestingGrounds = demarcateNestingGrounds(blockBody);
        
        int delimPos = blockBody.indexOf(delim);
        int dividerPos = blockBody.indexOf(dividerDelim);

        while (isInsideNestingGrounds(delimPos,nestingGrounds)) {
            delimPos = blockBody.indexOf(delim,delimPos+delim.length());
        }
        while (isInsideNestingGrounds(dividerPos,nestingGrounds)) {
            dividerPos = blockBody.indexOf(dividerDelim,dividerPos+dividerDelim.length());
        }
        
        if (dividerPos > -1) {
            if (delimPos > -1 && delimPos > dividerPos) {
                divider = blockBody.substring(dividerPos+dividerDelim.length(),delimPos);
                // remove divider section from block body
                String before = blockBody.substring(0,dividerPos);
                String after = blockBody.substring(delimPos);
                blockBody = before + after;
                delimPos -= divider.length() + dividerDelim.length();
            } else {
                divider = blockBody.substring(dividerPos+dividerDelim.length());
                // remove divider section from block body
                blockBody = blockBody.substring(0,dividerPos);
            }
            divider = doTrim ? smartTrim(divider) : divider;
        }
        
        if (delimPos > -1) {
            String template = blockBody.substring(0,delimPos);
            String onEmpty = blockBody.substring(delimPos+delim.length());
            this.rowTemplate = doTrim ? smartTrim(template) : template;
            this.emptyTemplate = doTrim ? onEmpty.trim() : onEmpty;
        } else {
            this.rowTemplate = doTrim ? smartTrim(blockBody) : blockBody;
        }
        
        if (divider != null) {
            registerOption("divider",divider);
        }
        
        return Loop.cookLoop(data, chunk, rowTemplate, emptyTemplate, options, isBlock);
    }
    
    private boolean isInsideNestingGrounds(int pos, int[] offLimits)
    {
        if (pos < 0) return false;
        if (offLimits == null) return false;
        
        for (int i=0; i<offLimits.length; i+=2) {
            int boundA = offLimits[i];
            int boundB = offLimits[i+1];
            if (pos < boundA) return false;
            if (pos < boundB) return true;
        }
        return false;
    }
    
    private int[] demarcateNestingGrounds(String blockBody)
    {
        String nestStart = this.chunk.tagStart + ".loop";
        int nestPos = blockBody.indexOf(nestStart);
        
        // easy case, no nesting
        if (nestPos < 0) return null;
        
        int[] bounds = null;
        while (nestPos > -1) {
            int[] endSpan = BlockTag.findMatchingBlockEnd(chunk, blockBody, nestPos+nestStart.length(), this);
            if (endSpan != null) {
                if (bounds == null) {
                    bounds = new int[]{nestPos,endSpan[1]};
                } else {
                    // grow each time -- yep, hugely inefficient,
                    // but hey, how often do we nest loops more than one deep?
                    int[] newBounds = new int[bounds.length+2];
                    System.arraycopy(bounds, 0, newBounds, 0, bounds.length);
                    newBounds[newBounds.length-2] = nestPos;
                    newBounds[newBounds.length-1] = endSpan[1];
                    bounds = newBounds;
                }
                nestPos = blockBody.indexOf(nestStart,endSpan[1]);
            } else {
                break;
            }
        }
        
        return bounds;
    }
    
    public String getBlockStartMarker()
    {
        return "loop";
    }
    
    public String getBlockEndMarker()
    {
        return "/loop";
    }
    
    private String smartTrim(String x)
    {
        String trimmed = x.trim();
        
        String trimOpt = (options != null) ? options.get("trim") : null;
        if (trimOpt != null && trimOpt.equals("all")) {
            // trim="all" disables smartTrim.
            return trimmed;
        }
        
        // if the block begins with (whitespace+) LF, trim initial line
        // otherwise, apply standard/complete trim.
        Pattern p = Pattern.compile("\n|\r\n|\r\r");
        Matcher m = p.matcher(x);
        
        if (m.find()) {
            int firstLF = m.start();
            if (x.substring(0,firstLF).trim().length() == 0) {
                return x.substring(m.end());
            }
        }
        
        return trimmed;
        
        // if there were any line break chars at the end, add just one back.
        /*
        Pattern p = Pattern.compile(".*[ \\t]*(\\r\\n|\\n|\\r\\r)[ \\t]*$");
        Matcher m = p.matcher(x);
        if (m.find()) {
            m.group(0);
            String eol = m.group(1);
            return trimmed + eol;
        } else {
            return trimmed;
        }*/
    }

}
