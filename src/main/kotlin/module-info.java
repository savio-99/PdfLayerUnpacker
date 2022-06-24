module org.pdflayoutsunpacker {
    exports org.pdflayoutsunpacker;

    requires java.base;
    requires java.desktop;
    requires kotlin.stdlib;

    requires commons.logging;
    requires org.apache.pdfbox;
    /*requires org.apache.xmpbox;
    requires org.apache.pdfbox.tools;
    requires org.apache.fontbox;
    requires org.apache.pdfbox.debugger;
    requires preflight;*/
}