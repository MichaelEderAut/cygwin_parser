package com.github.michaelederaut.cygwinparser;

import java.util.List;
import java.net.MalformedURLException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLEncoder;

import org.apache.commons.lang3.StringUtils;

import com.github.michaelederaut.basics.UrlProtocolUtils;
import com.googlecode.vfsjfilechooser2.accessories.connection.Protocol;

public class SetupConfigContents {
	
	public static final String S_encoding = java.nio.charset.StandardCharsets.UTF_8.toString();
	
	public static class Site {
		
//		public static enum Protocol {http, ftp};
		
		public URL O_url_download_source;
		public String S_dnr_url_encoded;
		public String S_site;
		public String S_region;
		public String S_state;
		public Protocol E_protocol;
		
	public Site (
			final String PI_S_url_download_source,
			final String PI_S_site,     
			final String PI_S_region,   
			final String PI_S_state) {
		
		RuntimeException E_rt; 
		URL O_url;
		String S_msg_1, S_rep_url_encoded;
		
		try {
			O_url = new URL(PI_S_url_download_source);
		} catch (MalformedURLException | NullPointerException PI_E_malformed_url) {
			S_msg_1 = "Unable to create Object of type: \'" + URL.class.getName() + "\' from \"" + PI_S_url_download_source + "\"" ;
			E_rt = new RuntimeException(S_msg_1, PI_E_malformed_url);
			throw E_rt;
		    }
		
		this.O_url_download_source = UrlProtocolUtils.FO_get_url(PI_S_url_download_source);
		this.S_site                = PI_S_site;  
		
		try {
			S_rep_url_encoded = URLEncoder.encode(
					PI_S_url_download_source, 
					S_encoding);
		} catch (UnsupportedEncodingException PI_E_unsupp_encoding) {
			S_msg_1 = "Unable to encode download-site: \"" + PI_S_url_download_source + "\" with \'" + S_encoding + "\'";
			E_rt = new RuntimeException(S_msg_1, PI_E_unsupp_encoding);
			throw E_rt;
		    } 
		
		this.S_dnr_url_encoded     = S_rep_url_encoded; 
		this.S_region              = PI_S_region;      
		this.S_state               = PI_S_state; 
		this.E_protocol            = UrlProtocolUtils.FE_get_protocol(this.O_url_download_source);
	    }
	}
	public String S_pna_last_cache;
	public List<Site> AO_mirror_list;
//	protected String S_pna_last_mirror;
	public int I_idx_last_mirror_f0 = -1;
	public String S_url_last_mirror;
	
	
	
}
