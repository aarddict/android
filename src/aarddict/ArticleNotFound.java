package aarddict;

public class ArticleNotFound extends Exception {
    
    public LookupWord word;

    ArticleNotFound(LookupWord word) {
        this.word = word;
    }
}
