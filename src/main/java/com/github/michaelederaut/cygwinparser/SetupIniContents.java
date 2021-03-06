package com.github.michaelederaut.cygwinparser;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Stack;
import java.util.TreeMap;
import java.util.TreeSet;
// import java.util.concurrent.ConcurrentSkipListMap;

import org.apache.commons.lang3.StringUtils;

import com.github.michaelederaut.basics.LineNbrRandomAccessFile;
import com.github.michaelederaut.basics.LineNbrRandomAccessFile.Line;

import org.apache.commons.lang3.ArrayUtils;

public class SetupIniContents {
	
	public static class ArchInfo {
		
		public enum DlStatus {unknown, notFound, prev, exists, isFile, sizeOk, hashOk};
		
		public static final int I_dl_status_prev = DlStatus.prev.ordinal();
		
		public int        I_size;
		public BigInteger O_hash_val;
	    public DlStatus   E_dl_status = DlStatus.unknown;
	    public String     S_version;
	    public String     S_pnr_archive;   // TODO
	
	public ArchInfo (
			final String PI_S_version,
			final String PI_S_size,
			final String PI_S_hash_val) {
		
		IllegalArgumentException E_ill_arg;
		RuntimeException E_rt;
		BigInteger O_hash_val;
		String S_msg_1, S_msg_2;
		
		O_hash_val = null;
		S_msg_1 = null;
		try {
			if (StringUtils.isBlank(PI_S_hash_val)) {
			   S_msg_1 = "Archive-hash-string \'" + PI_S_size + "\' must not be null or blank";
			   E_ill_arg = new IllegalArgumentException(S_msg_1);
			   throw E_ill_arg;
			   }
			else {
				try {
			       O_hash_val = new BigInteger(PI_S_hash_val, 16);
				} catch (NumberFormatException PI_E_nf) {
					S_msg_1 = "Unable to convert hash-value String \"" + PI_S_hash_val + "\" to BigInteger";
					E_ill_arg     = new IllegalArgumentException(S_msg_1, PI_E_nf);
					throw E_ill_arg;
				    }	
			     }
		} catch (IllegalArgumentException PI_E_ill_arg) {
			 S_msg_2 = "Instantiation of object of type: \'" + ArchInfo.class.getName() + "\' failed.";
		     E_rt = new RuntimeException(S_msg_2, PI_E_ill_arg);
		     throw E_rt;
			 }  
		FV_ctor(this, PI_S_version, PI_S_size, O_hash_val);
		}

	public ArchInfo (
			final String PI_S_version,
			final String PI_S_size,
			final BigInteger PI_O_hash_val) {
		
		FV_ctor(this, PI_S_version, PI_S_size, PI_O_hash_val);	
	}
	
	public ArchInfo (
			final String PI_S_version,
			final int PI_I_size,
			final BigInteger PI_O_hash_val) {
		
		FV_ctor(this, PI_S_version, PI_I_size, PI_O_hash_val);	
	}
	
	protected static void FV_ctor (
				ArchInfo PO_O_archinfo,
				final String PI_S_version,
				final int PI_I_size,
			    final BigInteger PI_O_hash_val) {
		
		IllegalArgumentException E_ill_arg;
		RuntimeException E_rt;
		String S_msg_1, S_msg_2;
		
		if (StringUtils.isBlank(PI_S_version)) {
		   S_msg_1 = "Package version nr.   \'" + PI_S_version + "\' must not be null or blank";
		   E_ill_arg = new IllegalArgumentException(S_msg_1);
		   S_msg_2 = "Instantiation of object of type: \'" + ArchInfo.class.getName() + "\' failed.";
		   E_rt = new RuntimeException(S_msg_2, E_ill_arg);
		   throw E_rt;
		   }
	  	
		PO_O_archinfo.S_version = PI_S_version;
		PO_O_archinfo.I_size = PI_I_size;
		PO_O_archinfo.O_hash_val = PI_O_hash_val;
		
		return;
	}

	protected static void FV_ctor (
				ArchInfo PO_O_archinfo,
				final String S_version,
				final String PI_S_size,
			    final BigInteger PI_O_hash_val) {
		
		IllegalArgumentException E_ill_arg;
		RuntimeException E_rt;
		int I_size;
		String S_msg_1, S_msg_2;
		
		I_size = -1;
		S_msg_1 = null;
		if (StringUtils.isBlank(PI_S_size)) {
		   S_msg_1 = "Archive-size-string \'" + PI_S_size + "\' must not be null or blank"; 
	       }
		else {
			try {
			   I_size = Integer.parseInt(PI_S_size);
		} catch (NumberFormatException PI_E_nf) {
			S_msg_1 = "Unable to convert size-parameter String \'" + PI_S_size+ "\' to Integer";
		    }
		}
	
		if (S_msg_1 != null) {
		   E_ill_arg = new IllegalArgumentException(S_msg_1);
		   S_msg_2 = "Instantiation of object of type: \'" + ArchInfo.class.getName() + "\' failed.";
		   E_rt = new RuntimeException(S_msg_2 , E_ill_arg);
		   throw E_rt;
		   }	
		
         FV_ctor(PO_O_archinfo, S_version, I_size, PI_O_hash_val);  
//		try {
//	       PO_O_archinfo.O_hash_val = new BigInteger(PI_S_hash_val, 16);
//		} catch (NumberFormatException PI_E_nf) {
//			S_msg_1 = "Unable to convert hash-value String \"" + PI_S_hash_val + "\" to BigInteger";
//			E_rt    = new RuntimeException(S_msg_1, PI_E_nf);
//			throw E_rt;
//		    }		
	return;	
	     }
	}  // END of ArchInfo
	
	public static class PckgArchInfos {
		 public String   S_name;
	     public ArchInfo AAO_archinfos[][];  // indexed by, {install, src}, 
	                                         // indexed by PurpseCompletion degrees {notFound, unknownVersion, prevOk, currentOk}
	     
	 public PckgArchInfos(
		//	 final String PI_S_pnr_archive,
			 final String PI_S_name,
			 final ArchInfo PI_AAO_archinfos[][]) {
		 
		IllegalArgumentException E_ill_arg;
		ArchInfo                 AO_archinfos[]; 
		RuntimeException         E_rt;
		String                   S_msg_1, S_msg_2;
		int i1, I_nbr_purposes_f1, I_nbr_purpose_completion_categories_f1;
		 
		 S_msg_1 = null;
		 if (StringUtils.isBlank(PI_S_name)) {
		     S_msg_1 = "Package Name \'" + PI_S_name + "\' must not be null or blank"; 
		     } 
		 else if (PI_AAO_archinfos == null) {
			 S_msg_1 = "Archive infos must not be null";
		     }
		 else {
			I_nbr_purposes_f1 = PI_AAO_archinfos.length;  
			if ((I_nbr_purposes_f1 < 1) || (I_nbr_purposes_f1 > 2)) {
				S_msg_1 = "Number of purposes: " + I_nbr_purposes_f1 + " not between 1 and " + 2 + "."; 
			    }
			else {
			   LOOP_PURPOSES: for (i1 = 0; i1 < 2; i1++) {
			      AO_archinfos = PI_AAO_archinfos[i1];	
			      if (AO_archinfos == null) {
					  S_msg_1 = "Purpose: " + i1 + " must not be null";
					  break LOOP_PURPOSES;
				      }
				I_nbr_purpose_completion_categories_f1 = AO_archinfos.length;
				if ((I_nbr_purpose_completion_categories_f1 < 1) || 
					(I_nbr_purpose_completion_categories_f1 > ArchiveChecker.I_nbr_purpose_completion_degrees)) {
			        S_msg_1 = "Number of archive infos: " + i1 + "/" + I_nbr_purpose_completion_categories_f1 + 
			    		      " not between 1 and " + ArchiveChecker.I_nbr_pckg_compl_degrees + ".";
			                  break LOOP_PURPOSES;
			          }
				   }
			    }
		    }  // END of: if (StringUtils.isBlank(PI_S_pnr_archive))
		  if (S_msg_1 != null) {
		     E_ill_arg = new IllegalArgumentException(S_msg_1);
		     S_msg_2 = "Instantiation of object of type: \'" + PckgArchInfos.class.getName() + "\' failed.";
		     E_rt = new RuntimeException(S_msg_2 , E_ill_arg);
		     throw E_rt;
			 }
		  this.S_name = PI_S_name;
		  this.AAO_archinfos   = PI_AAO_archinfos;
	     }
	}
	
	public static class PckgPosition {
       int I_line_nbr_f1;
       int I_pos_on_stack_f0;
       
       public PckgPosition (
           int PI_I_line_nbr_f1,
           int PI_I_pos_on_stack_f0) {
       
    	   this.I_line_nbr_f1     = PI_I_line_nbr_f1;
    	   this.I_pos_on_stack_f0 = PI_I_pos_on_stack_f0;
		   }
	}
	
	public static class PckgVersionInfo {
			
		public String     S_version;
		public ArchInfo   O_install, O_src;
	
	  public PckgVersionInfo (
			  final String PI_S_version,
			  final ArchInfo PI_O_install,
			  final ArchInfo PI_O_src) {
		  
			RuntimeException         E_rt;
			NullPointerException     E_np;
			AssertionError           E_assert;
			IllegalArgumentException E_ill_arg;
		  
		  String S_msg_1, S_msg_2;
		  
		  S_msg_1 = null;
		  if (StringUtils.isBlank(PI_S_version)) {
		     S_msg_1 = "Version-name \'" + PI_S_version + "\' must not be null or blank"; 
		     }
		  else if (PI_O_install == null) {
			  S_msg_1 = "Installation package info \'" + PI_O_install + "\' must not be null.";  
		      }
//		  else if (PI_O_src == null) {
//			  S_msg_1 = "Source package info \'" + PI_O_src + "\' must not be null.";  
//		      }
		  if (S_msg_1 != null) {
	         E_ill_arg = new IllegalArgumentException(S_msg_1);
	         S_msg_2 = "Instantiation of object of type: \'" + PckgVersionInfo.class.getName() + "\' failed.";
	         E_rt = new RuntimeException(S_msg_2 , E_ill_arg);
	         throw E_rt;
		     }
		  this.S_version = PI_S_version;
		  this.O_install = PI_O_install;
		  this.O_src     = PI_O_src;
	  }
	  
	 public PckgVersionInfo(final PckgVersionInfo PI_O_pckg_version_info) {
		 
		 this.S_version = PI_O_pckg_version_info.S_version;
		 this.O_install = PI_O_pckg_version_info.O_install;
		 this.O_src     = PI_O_pckg_version_info.O_src; 
	 }
	}
	
public static class PckgInfo extends PckgArchInfos  {
	public String S_sdesc, S_ldesc, AS_categories[], AS_requires[];
	
//	public PckgArchInfos O_pckg_arch_infos_current, O_pckg_arch_infos_prev;

	public static void FV_ctor(
			  final PckgInfo PO_O_pckg_info, 
			  final String PI_S_name,
			  final String PI_S_sdesc,
			  final String PI_S_ldesc,
			  final String PI_AS_categories[],
			  final String PI_AS_requires[]
			  ) {
		    
			RuntimeException         E_rt;
			NullPointerException     E_np;
			AssertionError           E_assert;
			IllegalArgumentException E_ill_arg;
			
			// int i1, I_size_array_f1;
			String S_msg_1, S_msg_2, S_category, S_version;
			
//		  MatcherAssert.assertThat("Package-Name \'" + PI_S_name + "\' must not be null or blank", 
//				   StringUtils.isNotBlank(PI_S_name));
		
		  S_msg_1 = null;	
          if (StringUtils.isBlank(PI_S_name)) {
        	 S_msg_1 = "Package-name \'" + PI_S_name + "\' must not be null or blank";
             }
          else if (StringUtils.isBlank(PI_S_sdesc)) {
        	 S_msg_1 = "Short description:\'" + PI_S_sdesc + "\' of package \'" + PI_S_name + "\' must not be null or blank"; 
             }
          else if (StringUtils.isBlank(PI_S_ldesc)) {
        	 S_msg_1 = "Long description: \'" + PI_S_ldesc + "\' of package \'" + PI_S_name + "\' must not be null or blank";
             }
          else if (ArrayUtils.isEmpty(PI_AS_categories)) {
        	  S_msg_1 = "Categories: \'" + ArrayUtils.toString(PI_AS_categories, "(String[])null") + "\' of package \'" + PI_S_name + "\' must not be null or empty";
              }
          else if (ArrayUtils.isEmpty(PI_AS_requires)) {
        	  S_msg_1 = "Requires: \'" + ArrayUtils.toString(PI_AS_requires, "(String[])null") + "\' of package \'" + PI_S_name + "\' must not be null or empty";
              }
//          else if (PI_O_archinfos_current == null) {
//        	 S_msg_1 = "Info about current package-version of type :\'" + PckgArchInfos.class.getName() + "\' of package \'" + PI_S_name + "\' must not be null"; 
//             }
          if (S_msg_1 != null) {
        	 E_ill_arg = new IllegalArgumentException(S_msg_1);
        	 S_msg_2 = "Instantiation of object of type: \'" + PckgInfo.class.getName() + "\' failed.";
        	 E_rt = new RuntimeException(S_msg_2 , E_ill_arg);
        	 throw E_rt;
          }
		   PO_O_pckg_info.S_name            = PI_S_name;
		   PO_O_pckg_info.S_sdesc           = PI_S_sdesc;
		   PO_O_pckg_info.S_ldesc           = PI_S_ldesc;
		   PO_O_pckg_info.AS_categories     = PI_AS_categories;
		   PO_O_pckg_info.AS_requires       = PI_AS_requires;
		//   PO_O_pckg_info.O_pckg_arch_infos_current = PI_O_archinfos_current;
		//   PO_O_pckg_info.O_pckg_arch_infos_prev    = PI_O_archinfos_prev;
	   }
		   
	  public PckgInfo (
			  final String PI_S_name,
			  final String PI_S_sdesc,
			  final String PI_S_ldesc,
			  final String PI_AS_categories[],
			  final String PI_AS_requires[],
			  final ArchInfo[] PI_AO_install,
			  final ArchInfo[] PI_AO_src) {
		  
		  super (PI_S_name, new ArchInfo[][] {PI_AO_install, PI_AO_src}); 
		 
		  
		   FV_ctor(
			this,
			PI_S_name,
			PI_S_sdesc,
			PI_S_ldesc,
			PI_AS_categories,
			PI_AS_requires);
		   
		    return;
		    }

public PckgInfo ( 
		  final String PI_S_name,
		  final String PI_S_sdesc,
		  final String PI_S_ldesc,
		  final String PI_AS_categories[],
		  final String PI_AS_requires[],
		  final PckgVersionInfo PI_O_version_current) {
	  
	super(PI_S_name, new ArchInfo[][] 
			{new ArchInfo[] 
				{new ArchInfo(
					PI_O_version_current.S_version, 
					PI_O_version_current.O_install.I_size, 
					PI_O_version_current.O_install.O_hash_val)}, 
				{new ArchInfo(
					PI_O_version_current.S_version,
					PI_O_version_current.O_src.I_size,
				    PI_O_version_current.O_src.O_hash_val)}});  
	 
	   FV_ctor(
		this,
		PI_S_name,
		PI_S_sdesc,
		PI_S_ldesc,
		PI_AS_categories,
		PI_AS_requires); 
		}

public PckgInfo ( 
		  final String PI_S_name,
		  final String PI_S_sdesc,
		  final String PI_S_ldesc,
		  final String PI_AS_categories[],
		  final String PI_AS_requires[],
		  final PckgVersionInfo PI_O_version_current,
		  final PckgVersionInfo PI_O_version_prev
		  ) {
	  
	super(PI_S_name, new ArchInfo[][] 
			{new ArchInfo[] 
				{new ArchInfo(
					PI_O_version_current.S_version, 
					PI_O_version_current.O_install.I_size, 
					PI_O_version_current.O_install.O_hash_val)}, 
				{new ArchInfo(
					PI_O_version_current.S_version,
					PI_O_version_current.O_src.I_size,
				    PI_O_version_current.O_src.O_hash_val)},
			    null,
			    {new ArchInfo(
					PI_O_version_prev.S_version, 
					PI_O_version_prev.O_install.I_size, 
					PI_O_version_prev.O_install.O_hash_val)}, 
				{new ArchInfo(
					PI_O_version_prev.S_version,
					PI_O_version_prev.O_src.I_size,
				    PI_O_version_prev.O_src.O_hash_val)},
			    
			});  
	 
	   FV_ctor(
		this,
		PI_S_name,
		PI_S_sdesc,
		PI_S_ldesc,
		PI_AS_categories,
		PI_AS_requires); 
		}
	}

    public Stack<PckgInfo>                      AO_pckg_info;
    public HashMap<Integer, Line>               HI_O_lines;
    public TreeMap<Long, Integer>               HL_address_to_line_nbrs;
    public HashMap<String, PckgPosition>        HS_package_names;
    public TreeMap<String, TreeSet<String>>     HAS_categories;  
    
    public SetupIniContents(LineNbrRandomAccessFile PI_O_buff_rdr) {
    	this.HI_O_lines              =  PI_O_buff_rdr.HI_lines;
    	this.HL_address_to_line_nbrs =  PI_O_buff_rdr.HL_address_to_line_nbrs;
    	
    	this.AO_pckg_info = new Stack<PckgInfo>();
        this.HS_package_names   = new HashMap<String, PckgPosition>();
        this.HAS_categories    = new TreeMap<String, TreeSet<String>>(); // category packages
    	}
        
    public void FV_add(
    	final String PI_S_pckg_name,
		final String PI_S_sdesc,
		final String PI_S_ldesc,
		final String PI_AS_categories[],
		final String PI_AS_requires[],
		final PckgVersionInfo PI_O_version_current, 
		final PckgVersionInfo PI_O_version_prev,
		final int PI_I_curr_line_f1) {
    	
    	  PckgInfo O_pckg_info;
    	  PckgPosition O_pckg_pos;
    	  String S_category, AS_categories[];
    	  int i1, I_nbr_categories_f1, I_idx_f0;
    	  TreeSet<String> AO_pckgs;
    	  
    	  O_pckg_info = new PckgInfo(
					PI_S_pckg_name,
					PI_S_sdesc,
					PI_S_ldesc,
					PI_AS_categories,
					PI_AS_requires,
					PI_O_version_current, 
					PI_O_version_prev);
    	  
    	  I_idx_f0 =  this.AO_pckg_info.size();
    	  this.AO_pckg_info.add(O_pckg_info);
    	  O_pckg_pos = new PckgPosition(PI_I_curr_line_f1, I_idx_f0);
    	  
    	  this.HS_package_names.put(PI_S_pckg_name, O_pckg_pos);
    	  I_nbr_categories_f1 = PI_AS_categories.length;
    	  for (i1 = 0; i1 < I_nbr_categories_f1; i1++) {
    		  S_category = PI_AS_categories[i1];
    		  if (HAS_categories.containsKey(S_category)) {
    			  HAS_categories.get(S_category).add(PI_S_pckg_name);
    		      }
    		  else {
    			  AO_pckgs = new TreeSet<String>() {{add(PI_S_pckg_name); }};
    			  HAS_categories.put(S_category, AO_pckgs);
    		  }
    	  }
    	  
    	return;
    }
    
 //-------
    	
    public void FV_add(
    	final String PI_S_pckg_name,
		final String PI_S_sdesc,
		final String PI_S_ldesc,
		final String PI_AS_categories[],
		final String PI_AS_requires[],
		final PckgArchInfos PI_AO_archinfos_current,
		final PckgArchInfos PI_AO_archinfos_prev,
		final int PI_I_curr_line_f1) {
    	
    	  PckgInfo O_pckg_info;
    	  PckgPosition O_pckg_pos;
    	  String S_category, AS_categories[];
    	  int i1, I_nbr_categories_f1, I_idx_f0;
    	  TreeSet<String> AO_pckgs;
    	  
    	  O_pckg_info = new PckgInfo(
					PI_S_pckg_name,
					PI_S_sdesc,
					PI_S_ldesc,
					PI_AS_categories,
					PI_AS_requires,
					PI_AO_archinfos_current.AAO_archinfos[0], // TODO
					PI_AO_archinfos_prev.AAO_archinfos[0]); // TODO
    	  
    	  I_idx_f0 =  this.AO_pckg_info.size();
    	  this.AO_pckg_info.add(O_pckg_info);
    	  O_pckg_pos = new PckgPosition(PI_I_curr_line_f1, I_idx_f0);
    	  
    	  this.HS_package_names.put(PI_S_pckg_name, O_pckg_pos);
    	  I_nbr_categories_f1 = PI_AS_categories.length;
    	  for (i1 = 0; i1 < I_nbr_categories_f1; i1++) {
    		  S_category = PI_AS_categories[i1];
    		  if (HAS_categories.containsKey(S_category)) {
    			  HAS_categories.get(S_category).add(PI_S_pckg_name);
    		      }
    		  else {
    			  AO_pckgs = new TreeSet<String>() {{add(PI_S_pckg_name); }};
    			  HAS_categories.put(S_category, AO_pckgs);
    		  }
    	  }
    	  
    	return;
 //-------	
 
       }
	}