package com.github.michaelederaut.cygwinparser;

import static com.github.michaelederaut.basics.RegexpUtils.GroupMatchResult;
import static com.github.michaelederaut.basics.RegexpUtils.NamedPattern;

import java.io.IOException;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.*;

import com.github.michaelederaut.basics.EnumReflectionUtils;
import com.github.michaelederaut.basics.LineNbrRandomAccessFile;
import com.github.michaelederaut.basics.RegexpUtils;
import com.github.michaelederaut.basics.StreamUtils;
import com.github.michaelederaut.basics.LineNbrRandomAccessFile.ReadLinePolicy;
import com.github.michaelederaut.cygwinparser.SetupIniContents.ArchInfo;
import com.github.michaelederaut.cygwinparser.SetupIniContents.PckgArchInfos;
import com.github.michaelederaut.cygwinparser.SetupIniContents.PckgVersionInfo;
import com.github.michaelederaut.cygwinparser.SetupIniContents.PckgInfo;
import static com.github.michaelederaut.cygwinparser.ArchiveChecker.I_nbr_pckg_compl_degrees;

public class IniFileParser {
	
private static final String PKG_NAME          = "pkgname";
private static final String DESCRIPTION_SHORT = "sdesc";
private static final String DESCRIPTION_LONG  = "ldesc";
private static final String CLOSING_QUOTE     = "closingquote";
private static final String CATEGORY          = "category";
private static final String REQUIRES          = "requires";
private static final String VERSION           = "version";
private static final String INSTALL           = "install";
private static final String SOURCE            = "source";

private static final String S_re_comment_line = "^\\#.*$";
private static final NamedPattern P_comment_line =  NamedPattern.FO_compile(S_re_comment_line);

// private static final String S_re_pkg_hdr = "^@\\s+({" + PKG_NAME + "}[A-Za-z0-9._+\\-]+)\\s*$";
private static final String S_re_pkg_hdr = "^@\\s+(?<" + PKG_NAME + ">[A-Za-z0-9._+\\-]+)\\s*$";
private static final NamedPattern P_pkg_hdr = NamedPattern.FO_compile(S_re_pkg_hdr);

// private static final String S_re_sdesc = "^" + DESCRIPTION_SHORT + "\\:\\s+\"({" + DESCRIPTION_SHORT + "}[^\"]*?)\"$";
private static final String S_re_sdesc = "^" + DESCRIPTION_SHORT + "\\:\\s+\"(?<" + DESCRIPTION_SHORT + ">[^\"]*?)\"$";
private static final NamedPattern P_sdesc =  NamedPattern.FO_compile(S_re_sdesc);

// private static final String S_re_ldesc = "^" + DESCRIPTION_LONG + "\\:\\s+\"({" + DESCRIPTION_LONG + "}[^\"]*?)({" + CLOSING_QUOTE + "}\")?$";
private static final String S_re_ldesc = "^" + DESCRIPTION_LONG + "\\:\\s+\"(?<" + DESCRIPTION_LONG +  ">[^\"]*?)(?<" + CLOSING_QUOTE + ">\")?$";
private static final NamedPattern P_ldesc =  NamedPattern.FO_compile(S_re_ldesc);

// private static final String S_re_ldesc_cont    = "^({" + DESCRIPTION_LONG + "}[^\"]*?)({" + CLOSING_QUOTE + "}\")?$";
private static final String S_re_ldesc_cont    = "^(?<" + DESCRIPTION_LONG + ">[^\"]*?)(?<" + CLOSING_QUOTE + ">\")?$";
private static final NamedPattern P_ldesc_cont = NamedPattern.FO_compile(S_re_ldesc_cont);

// private static final String S_re_catgegory = "^" + CATEGORY + "\\:\\s+({" + CATEGORY + "}[a-zA-Z0-9._+\\-]+)\\s*$";
private static final String S_re_catgegory = "^" + CATEGORY + "\\:\\s+(?<" + CATEGORY + ">[a-zA-Z0-9._+\\-]+)\\s*$";
static final NamedPattern   P_category     =  NamedPattern.FO_compile(S_re_catgegory);

// private static final String S_re_requires = "^" + REQUIRES + "\\:\\s+({" + REQUIRES + "}[^\"]*?)$";
private static final String S_re_requires = "^" + REQUIRES + "\\:\\s+(?<" + REQUIRES + ">[^\"]*?)$";
private static final NamedPattern P_requires =  NamedPattern.FO_compile(S_re_requires);

// private static final String S_re_version = "^" + VERSION + "\\:\\s+({" + VERSION + "}[a-zA-Z0-9._+\\-]+)\\s*$";
private static final String S_re_version = "^" + VERSION + "\\:\\s+(?<" + VERSION + ">[a-zA-Z0-9._+\\-]+)\\s*$";
static final NamedPattern   P_version    =  NamedPattern.FO_compile(S_re_version);

private static final String S_re_archinfo = "^([a-zA-Z0-9._+\\-/]+)\\s+(\\d+)\\s+(\\p{XDigit}{128})$";
static final NamedPattern   P_archinfo    =  NamedPattern.FO_compile(S_re_archinfo);

// private static final String S_re_install = "^" + INSTALL + "\\:\\s+({" + INSTALL + "}[^\"]*?)$";
private static final String S_re_install = "^" + INSTALL + "\\:\\s+(?<" + INSTALL + ">[^\"]*?)$";
private static final NamedPattern P_install =  NamedPattern.FO_compile(S_re_install);

//private static final String S_re_source = "^" + SOURCE + "\\:\\s+({" + SOURCE + "}[^\"]*?)$";
private static final String S_re_source = "^" + SOURCE + "\\:\\s+(?<" + SOURCE + ">[^\"]*?)$";
private static final NamedPattern P_source =  NamedPattern.FO_compile(S_re_source);

private static final String PREV_MARKER = "[prev]";

// private enum ParsingState {Init, PkgName, Sdesc, Ldesc, LdescCont, Category, Requires, InterPgk, Finished};

private enum ParsingState {Init, PkgName, Sdesc, Ldesc, LdescCont, Category, Requires, 
	Version,     Install,    Source,     Prev,
	VersionPrev, InstallPrev, SourcePrev,
	InterPgk, Finished;
};

private final static int  I_parsing_state_Source        = ParsingState.Source.ordinal();
// private final static int  I_parsing_state_VersionPrev   = ParsingState.VersionPrev.ordinal();
private final static int  I_parsing_state_finished      = ParsingState.Finished.ordinal();
private final static int I_prev_offset = ParsingState.VersionPrev.ordinal() - ParsingState.Version.ordinal();

private static class MutableParsingState  extends MutableObject<ParsingState> {
	
	public ParsingState E_parsing_state;
	public int          I_offset;
	
	public MutableParsingState(final ParsingState PI_E_parsing_state) {
		this.E_parsing_state = PI_E_parsing_state;
		return;
	}
	
	public MutableParsingState(
			final ParsingState PI_E_parsing_state,
			final int          PI_I_offset) {
		this.E_parsing_state = PI_E_parsing_state;
		this.I_offset        = PI_I_offset;
	}
	
	@Override
	public ParsingState getValue() {
		return this.E_parsing_state;
	}
	
	
	@Override
	public void setValue(final ParsingState PI_E_parsing_state) {
		this.E_parsing_state = PI_E_parsing_state; 
	}
}

private static SetupIniContents.PckgArchInfos FO_parse_pck_info (
		final LineNbrRandomAccessFile    PB_O_buff_reader,
		final MutableParsingState        PB_OM_parsing_state) {
	
	RuntimeException                 E_rt;
	AssertionError                   E_assert;

	ParsingState                     E_parsing_state;
	GroupMatchResult                 O_grp_match_res;
//	PckgArchInfos                    O_pckg_arch_infos_install, O_pck_arch_infos_src;
	ArchInfo                         O_arch_info_install, O_arch_info_src;
                 
	String                           S_msg_1, S_msg_2, S_line_input, S_pna_inp,
	                                 S_version, S_pn_archive, S_size_install, S_size_src, S_chk_sum_install, S_chk_sum_src,
	                                 S_archinfo,  AS_numbered_groups[];
	int                              I_line_nbr_f1, I_offs_f1;
	
	PckgArchInfos O_retval_pck_arch_infos = null;
	E_parsing_state = PB_OM_parsing_state.getValue();

	S_version = null;
	O_arch_info_install = null;
	O_arch_info_src     = null;
	
	S_pna_inp = PB_O_buff_reader.S_pn;
//	S_msg_1 = "Now reading lines from file: \"" + S_pna_inp + "\"";
//	System.out.println(S_msg_1);
	
	S_line_input = PB_O_buff_reader.FS_re_read_line();
	I_line_nbr_f1 = PB_O_buff_reader.I_curr_line_nbr;
	S_size_install    = null;
	S_chk_sum_install = null;
	S_size_src        = null;
	S_chk_sum_src     = null;
	
	LOOP_INPUT_LINES: while (E_parsing_state.ordinal() < I_parsing_state_Source) {
		if (S_line_input == null) {
		   if  ((E_parsing_state == ParsingState.Install) || (E_parsing_state == ParsingState.Source)) {
			  E_parsing_state = ParsingState.Finished;
		      }
		   else {
			  S_msg_1 = "Unexepectd end of file at line: " +  I_line_nbr_f1 + ".";
			  E_assert = new AssertionError(S_msg_1);
			  S_msg_2 = "Unable to instantiate new object of type: \'" + 
			             SetupIniContents.PckgVersionInfo.class.getName() + "\'";
			  E_rt = new RuntimeException(S_msg_2, E_assert);
			  throw E_rt;
		      }
		   }
		else if (E_parsing_state == ParsingState.Requires) {
			O_grp_match_res = RegexpUtils.FO_match(S_line_input, P_version.O_patt);
			if (O_grp_match_res.I_map_size_f1 >= 1) {
			   S_version = O_grp_match_res.HS_named_groups.get(VERSION).S_grp_val;
			   E_parsing_state = ParsingState.Version;		
			   }
		    }
		else if (E_parsing_state == ParsingState.Version) {
			S_size_install    = null;
			S_chk_sum_install = null;
			S_size_src        = null;
			S_chk_sum_src     = null;
			O_grp_match_res = RegexpUtils.FO_match(S_line_input, P_install.O_patt);
			if (O_grp_match_res.I_map_size_f1 >= 1) {
			   S_archinfo = O_grp_match_res.HS_named_groups.get(INSTALL).S_grp_val;
			   O_grp_match_res = RegexpUtils.FO_match(S_archinfo, P_archinfo.O_patt);
			   if (O_grp_match_res.I_array_size_f1 >= 4) {
				  AS_numbered_groups = O_grp_match_res.AS_numbered_groups;
				  S_pn_archive = AS_numbered_groups[1];
				  S_size_install       = AS_numbered_groups[2];
				  S_chk_sum_install    = AS_numbered_groups[3];
				  E_parsing_state = ParsingState.Install;
			      }
			   }
		    }
		else if (E_parsing_state == ParsingState.Install) {
			O_grp_match_res = RegexpUtils.FO_match(S_line_input, P_source.O_patt);
			if (O_grp_match_res.I_map_size_f1 >= 1) {
			   S_archinfo = O_grp_match_res.HS_named_groups.get(SOURCE).S_grp_val;
			   O_grp_match_res = RegexpUtils.FO_match(S_archinfo, P_archinfo.O_patt);
			   if (O_grp_match_res.I_array_size_f1 >= 4) {
				  AS_numbered_groups = O_grp_match_res.AS_numbered_groups;
				  S_pn_archive = AS_numbered_groups[1];
				  S_size_src       = AS_numbered_groups[2];
				  S_chk_sum_src    = AS_numbered_groups[3];
				  O_retval_pck_arch_infos  =  new PckgArchInfos(
						   S_pn_archive,
						   S_version,
						   new ArchInfo[][]{
							   {new ArchInfo(S_size_install, S_chk_sum_install)},
							   {new ArchInfo(S_size_src,     S_chk_sum_src)}});
						  
				  E_parsing_state = ParsingState.Source;
			      }
			   }
			   else if (StringUtils.startsWith(S_line_input, PREV_MARKER)) {
				   E_parsing_state = ParsingState.Prev;	
			   }
		    }
		try {
			S_line_input = PB_O_buff_reader.FS_readLine();
			I_line_nbr_f1 = PB_O_buff_reader.I_curr_line_nbr;
		} catch (RuntimeException PI_E_rt) {
		    S_msg_1 = "Error reading input file \"" + PB_O_buff_reader.S_pn + "\" at line: " + I_line_nbr_f1;
		    E_rt = new RuntimeException(S_msg_1, PI_E_rt);
		  throw E_rt;
		  }
	}   // END while
	
	I_offs_f1 =  PB_OM_parsing_state.I_offset;
	if (I_offs_f1 != 0) {
	   EnumReflectionUtils.FV_modify_ordinal_by(E_parsing_state, I_offs_f1);
	   }
	PB_OM_parsing_state.setValue(E_parsing_state);
	return O_retval_pck_arch_infos;
}

public static SetupIniContents FO_parse(final LineNbrRandomAccessFile PI_O_buff_reader) {
		SetupIniContents O_retval_setup_ini_contents;
		
		RuntimeException     E_rt;
		NullPointerException E_np;
		AssertionError       E_ass;
		
		ParsingState E_parsing_state;
		GroupMatchResult O_grp_match_res;
	//	SetupIniContents.PckgVersionInfo O_pkg_vers_info, O_pkg_vers_info_prev;
		SetupIniContents.PckgArchInfos O_pkg_arch_infos, O_pkg_arch_infos_prev;
		
		MutableParsingState OM_parsing_state;
		
		int I_line_nbr_f1, I_line_nbr_of_pckg_start_f1;
		StringBuilder SB_description_long;
		String S_msg_1, S_msg_2, S_pna_inp, S_line_input,
		S_pckg_name, S_description_short, S_description_long, S_description_long_part, S_closing_quote, 
		S_category, AS_categories[], S_requires, AS_requires[];
		ReadLinePolicy E_read_line_policy;
		
		O_retval_setup_ini_contents = new SetupIniContents(PI_O_buff_reader);
		SB_description_long = new StringBuilder();
		E_parsing_state = ParsingState.Init;
		
		S_pckg_name           = null;
		S_description_short   = null;
		S_description_long    = null;
		AS_categories         = null;
		AS_requires           = null;
		O_pkg_arch_infos      = null; 
		O_pkg_arch_infos_prev = null;
		
		I_line_nbr_f1               = 0;
		I_line_nbr_of_pckg_start_f1 = 0;
		S_line_input                = null;
		E_read_line_policy          = ReadLinePolicy.ReadNext;
		
		S_pna_inp = PI_O_buff_reader.S_pn;
		S_msg_1 = "Now reading lines from file: \'" + S_pna_inp + "\'";
	    System.out.println(S_msg_1);
		
		LOOP_INPUT_LINES: while (E_parsing_state.ordinal() < I_parsing_state_finished) {
			I_line_nbr_f1 = PI_O_buff_reader.I_curr_line_nbr;
			switch (E_read_line_policy) {
				case ReadNext: 
					try {
						S_line_input = PI_O_buff_reader.FS_readLine();
						I_line_nbr_f1 = PI_O_buff_reader.I_curr_line_nbr;
					} catch (RuntimeException PI_E_rt) {
						S_msg_1 = "Error reading input file at line: " + I_line_nbr_f1;
						E_rt = new RuntimeException(S_msg_1, PI_E_rt);
						throw E_rt;
					    }
					break;
				case ReRead: 
						try {
							S_line_input = PI_O_buff_reader.FS_re_read_line();
							I_line_nbr_f1 = PI_O_buff_reader.I_curr_line_nbr;
						} catch (RuntimeException PI_E_rt) {
							S_msg_1 = "Error re-reading input file at line: " + I_line_nbr_f1;
							E_rt = new RuntimeException(S_msg_1, PI_E_rt);
							throw E_rt;
						    }
				case SkipRead:
						E_read_line_policy = ReadLinePolicy.ReadNext;
						break;
			}
			if (S_line_input == null) {
				E_parsing_state = ParsingState.Finished;
				   O_retval_setup_ini_contents.FV_add(
						    S_pckg_name,
							S_description_short,
							S_description_long,
							AS_categories,
							AS_requires,
							O_pkg_arch_infos, 
							O_pkg_arch_infos_prev,
							I_line_nbr_of_pckg_start_f1);   
				   break LOOP_INPUT_LINES;
			    }
			if (StringUtils.isBlank(S_line_input)) {
				continue LOOP_INPUT_LINES;
			    }
			O_grp_match_res = RegexpUtils.FO_match(S_line_input, P_comment_line.O_patt);
			if (O_grp_match_res.I_array_size_f1 >= 1) {
				continue LOOP_INPUT_LINES;
			    }
			if ((E_parsing_state == ParsingState.InterPgk) || (E_parsing_state == ParsingState.Init)) {
				O_grp_match_res = RegexpUtils.FO_match(S_line_input, P_pkg_hdr.O_patt);
				if (O_grp_match_res.I_map_size_f1 >= 1) {
				   if (E_parsing_state == ParsingState.InterPgk) {
					   O_retval_setup_ini_contents.FV_add(
						   S_pckg_name,
						   S_description_short,
						   S_description_long,
						   AS_categories,
						   AS_requires,
						   O_pkg_arch_infos, 
						   O_pkg_arch_infos_prev,
						   I_line_nbr_of_pckg_start_f1);   
					   
					   S_description_short  = null;
					   S_description_long   = null;
					   AS_categories          = null;
					   AS_requires          = null;
					   O_pkg_arch_infos      = null; 
					   O_pkg_arch_infos_prev = null;
				       }
					
					S_pckg_name = O_grp_match_res.HS_named_groups.get(PKG_NAME).S_grp_val;
					I_line_nbr_of_pckg_start_f1 = I_line_nbr_f1;
					E_parsing_state = ParsingState.PkgName;
//					S_msg_1 = "Package-name: " + S_pkg_name;
//					System.out.println(S_msg_1);
					
			        }
			     }
			else if (E_parsing_state == ParsingState.PkgName) {
				O_grp_match_res = RegexpUtils.FO_match(S_line_input, P_sdesc.O_patt);
				if (O_grp_match_res.I_map_size_f1 >= 1) {
					S_description_short = O_grp_match_res.HS_named_groups.get(DESCRIPTION_SHORT).S_grp_val;
					E_parsing_state = ParsingState.Sdesc;
//					S_msg_1 = "Short-Descrition: " + S_description_short;
//					System.out.println(S_msg_1);
			        }
		     }
			else if (E_parsing_state == ParsingState.Sdesc) {
				O_grp_match_res = RegexpUtils.FO_match(S_line_input, P_ldesc.O_patt);
				SB_description_long = new StringBuilder();
				if (O_grp_match_res.I_map_size_f1 >= 2) {
				   S_description_long = O_grp_match_res.HS_named_groups.get(DESCRIPTION_LONG).S_grp_val;	
			       S_closing_quote = O_grp_match_res.HS_named_groups.get(CLOSING_QUOTE).S_grp_val;
			       if (StringUtils.isEmpty(S_closing_quote)) {  // line containing long description will be continued
			    	   E_parsing_state = ParsingState.LdescCont;
			    	   SB_description_long = new StringBuilder(S_description_long);
			           }
			       else {
			    	   E_parsing_state = ParsingState.Ldesc; // line containing long description complete
			           } 
				   S_description_long_part = O_grp_match_res.HS_named_groups.get(DESCRIPTION_LONG).S_grp_val;
			      // System.out.println(ParsingState.Sdesc.name());
				}
			}
			else if (E_parsing_state == ParsingState.LdescCont)  {
				O_grp_match_res = RegexpUtils.FO_match(S_line_input, P_ldesc_cont.O_patt);
				if (O_grp_match_res.I_map_size_f1 >= 2) {
					S_description_long_part =  O_grp_match_res.HS_named_groups.get(DESCRIPTION_LONG).S_grp_val;
					
					if (StringUtils.isNotBlank(S_description_long_part)) {
						SB_description_long.append(" ");
					   }
					SB_description_long.append(S_description_long_part);
					S_closing_quote = O_grp_match_res.HS_named_groups.get(CLOSING_QUOTE).S_grp_val;
					if (StringUtils.isNotEmpty(S_closing_quote)) {  // lines containing long description complete
					   E_parsing_state = ParsingState.Ldesc;
					   S_description_long = SB_description_long.toString(); 
					 }
				}
			}
			else if (E_parsing_state == ParsingState.Ldesc) {
				O_grp_match_res = RegexpUtils.FO_match(S_line_input, P_category.O_patt);
				if (O_grp_match_res.I_map_size_f1 >= 1) {
				   S_category = O_grp_match_res.HS_named_groups.get(CATEGORY).S_grp_val;
				   AS_categories = S_category.split("\\s+");
				   E_parsing_state = ParsingState.Category;
				   }
			    }
			else if (E_parsing_state == ParsingState.Category)  {
				O_grp_match_res = RegexpUtils.FO_match(S_line_input, P_requires.O_patt);
				if (O_grp_match_res.I_map_size_f1 >= 1) {
				   S_requires = O_grp_match_res.HS_named_groups.get(REQUIRES).S_grp_val;
				   AS_requires = S_requires.split("\\s+");
				   E_parsing_state = ParsingState.Requires;
				   }
			    }
			else if (E_parsing_state == ParsingState.Requires)  {
				OM_parsing_state = new MutableParsingState(E_parsing_state);
				O_pkg_arch_infos = FO_parse_pck_info(PI_O_buff_reader, OM_parsing_state);
				if (O_pkg_arch_infos == null) {
					S_msg_1 =  "Error when parsing \'" + E_parsing_state.toString() + "\'";
					E_np = new NullPointerException(S_msg_1);
					S_msg_2 = "Error at line: " + I_line_nbr_f1 + "\n" +
				              "last confirmed sucessful parsing state: " + OM_parsing_state.getValue().toString();
					E_ass = new AssertionError(S_msg_2, E_np);
					throw E_ass;
				   }
				else {
				   E_parsing_state = OM_parsing_state.getValue();
				   E_read_line_policy = ReadLinePolicy.ReRead;
				   }
			    }
			else if ((E_parsing_state == ParsingState.Source) || (E_parsing_state == ParsingState.Install)) {
				if (StringUtils.startsWith(S_line_input, PREV_MARKER)) {
				   E_parsing_state = ParsingState.Prev;
				   }
				else {
					E_parsing_state = ParsingState.InterPgk;  // no [prev] line_found
					O_pkg_arch_infos_prev = null;
					E_read_line_policy   = ReadLinePolicy.SkipRead;
				    }
		        }
			else if (E_parsing_state == ParsingState.Prev) {
				OM_parsing_state = new MutableParsingState(ParsingState.Requires /*,  I_prev_offset */);
				O_pkg_arch_infos_prev = FO_parse_pck_info(PI_O_buff_reader, OM_parsing_state); // previous versions of the archives
				if (O_pkg_arch_infos_prev == null) {
					S_msg_1 =  "Error when parsing \'" + E_parsing_state.toString() + "\'";
					E_np = new NullPointerException(S_msg_1);
					S_msg_2 = "Error at line: " + I_line_nbr_f1 + "\n" +
				              "last confirmed sucessful parsing state: " + OM_parsing_state.getValue().toString();
					E_ass = new AssertionError(S_msg_2, E_np);
					throw E_ass;
				   }
				else {
				   E_parsing_state = OM_parsing_state.getValue();
				   if ((E_parsing_state == ParsingState.Install) || (E_parsing_state == ParsingState.Source)) {
					   E_parsing_state = ParsingState.InterPgk;
				       }
				   }
			    }
			if (E_parsing_state == ParsingState.Finished) {
			   O_retval_setup_ini_contents.FV_add(
					   S_pckg_name,
					   S_description_short,
					   S_description_long,
					   AS_categories,
					   AS_requires,
					   O_pkg_arch_infos, 
					   O_pkg_arch_infos_prev,
					   I_line_nbr_of_pckg_start_f1); 
			   
			   break LOOP_INPUT_LINES;	
			}
		}
        return O_retval_setup_ini_contents;	    
     }
}

