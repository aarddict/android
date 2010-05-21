/**
 * 
 */
package aarddict;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

public final class Article {

    public UUID     dictionaryUUID;  
    public String   volumeId;
    public String   title;
    public String   section;
    public long     pointer;
    private String  redirect;
    public String   text;
	public String   redirectedFromTitle;

    public Article() {            
    }
    
    public Article(Article that) {
        this.dictionaryUUID = that.dictionaryUUID;
        this.volumeId = that.volumeId;
        this.title = that.title;
        this.section = that.section;
        this.pointer = that.pointer;
        this.redirect = that.redirect;
        this.text = that.text;
        this.redirectedFromTitle = that.redirectedFromTitle;
    }

    @SuppressWarnings("unchecked")
    static Article fromJsonStr(String serializedArticle) throws IOException {
    	Object[] articleTuple = Volume.mapper.readValue(serializedArticle, Object[].class);
        Article article = new Article();
        article.text = String.valueOf(articleTuple[0]);
        if (articleTuple.length == 3) {
        	Map metadata = (Map)articleTuple[2];                
            if (metadata.containsKey("r")) {
                article.redirect = String.valueOf(metadata.get("r"));
            }
            else if (metadata.containsKey("redirect")) {
                article.redirect = String.valueOf(metadata.get("redirect"));
            }
        }            
        return article;
    }

    public String getRedirect() {
        if (this.redirect != null && this.section != null) {
            return this.redirect + "#" + this.section;
        }
        return this.redirect;
    }
    
    public boolean isRedirect() {
        return this.redirect != null;
    }
    
    public boolean eqalsIgnoreSection(Article other) {
        return volumeId.equals(other.volumeId) && pointer == other.pointer;
    }
}