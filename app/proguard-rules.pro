# Os nomes dos tipos de filtro são gravados nos presets (FilterType.name / valueOf): não podem ser renomeados.
-keep enum com.meuapp.player.audio.FilterType { *; }
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
