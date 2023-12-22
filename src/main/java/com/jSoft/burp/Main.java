package com.jSoft.burp;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import com.coreyd97.BurpExtenderUtilities.DefaultGsonProvider;
import com.coreyd97.BurpExtenderUtilities.PersistedList;
import com.coreyd97.BurpExtenderUtilities.PersistedMap;
import com.coreyd97.BurpExtenderUtilities.PersistedObject;
import com.coreyd97.BurpExtenderUtilities.PersistedSet;
import com.coreyd97.BurpExtenderUtilities.Preferences;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class Main implements BurpExtension{
  @Override
  public void initialize(MontoyaApi api){
    ///////////////////////////////////////////
    // INSTANTIATING AUTO-PERSISTING OBJECTS //
    ///////////////////////////////////////////
    PersistedSet<Path> minimalPathSet = new PersistedSet<>(
      api, "minimalPathSet", Preferences.Visibility.VOLATILE
    );

    //specify a default value
    PersistedSet<String> set = new PersistedSet<>(
      api, "testSet", new HashSet<>(),
      Preferences.Visibility.PROJECT
    );

    //specify a default value and the type to serialize/deserialize as
    PersistedSet<String> oldSet = new PersistedSet<>(
      api, "oldSet",
      new TypeToken<HashSet<String>>(){}, new HashSet<>(),
      Preferences.Visibility.PROJECT
    );
    //oldSet.save();

    //specify the type without a default value
    PersistedList<String> lst = new PersistedList<>(
      api, "testLst", new ArrayList<>(),
      Preferences.Visibility.PROJECT
    );

    //specify a default without a type
    PersistedMap<String, String, HashMap<String, String>> map = new PersistedMap<>(
      api, "testMap", new HashMap<>(),
      Preferences.Visibility.PROJECT
    );

    //Create a PersistedObject
    Settings2 settings = new Settings2(api, "settings2", Preferences.Visibility.PROJECT);

    ///////////////////////////////////
    // USING AUTO-PERSISTING OBJECTS //
    ///////////////////////////////////
    //note: the auto-persisting collections are intended to work
    //  without the user needing to create any custom implementations
    //minimalPathSet.contains(Path.of("/etc/passwd")); //will fail because no default was passed in... so minimalPathSet is null
    minimalPathSet.set(new HashSet<>());//required since no default is null
    minimalPathSet.add(Path.of("/some/path")); //used like a normal set... but automatically persists
    //list and map can be used similarly
    lst.add("test");//used normal but auto-persists

    for(String str : lst){
      //and modifications in the loop must use an explicit call to `save()`
      str += "something";
      lst.save();
    }

    //PersistedObjects are similar but require the user to create a custom implementation
    //  (because there is no way for us to know in advance what fields the user wants to persist)
    //note: This is just an example. If you prefer getters to start with the word "get"
    //  and your setters to start with the word "set"... you can do that.
    //  The implementation of the class that extends `PersistedObject` is left up to the users of the library
    settings.activeEnabled();//get value
    settings.activeEnabled(false);//set value (auto-persists based on user-defined implementation)
    settings.inFilePaths().add(Path.of("/test/path"));//will NOT auto-persist
    settings.save();//required to persist change from previous line
    settings.addInFile(Path.of("/test/path/2"));//WILL auto-persist based on user-defined implementation

    List<Path> inFilePaths = settings.inFilePaths();
    for(Path path : inFilePaths){
      //modifications to inFilePaths are generally unsafe here
      //  but even if using a ConcurrentList or something like that...
      //  modifications will DEFINITELY not be auto-persisted
      settings.save();
    }

    //////////////////////////////////
    // MULTI-LEVEL PERSISTED OBJECT //
    //////////////////////////////////
    ExtensionSettings extSettings = new ExtensionSettings(api, "extensionSettings", Preferences.Visibility.PROJECT);
    api.logging().logToError("extSettings = \n" + extSettings);
    extSettings.reset();
    api.logging().logToError("\n\nextSettings = \n" + extSettings);
    extSettings.subSettings1().enabled(true);
    api.logging().logToError("\n\nextSettings = \n" + extSettings);
  }
}

class GsonProvider extends DefaultGsonProvider{
  public GsonProvider(){
    super();
    this.registerTypeHierarchyAdapter(Path.class, new PathGsonifier());
  }

  static final class PathGsonifier implements JsonDeserializer<Path>, JsonSerializer<Path>{
    @Override
    public Path deserialize(
      JsonElement jsonElement, Type type, JsonDeserializationContext context
    ) throws JsonParseException{
      return Path.of(jsonElement.getAsString());
    }

    @Override
    public JsonElement serialize(
      Path path, Type type, JsonSerializationContext context
    ){
      return new JsonPrimitive(path.toString());
    }
  }
}

class Settings2 extends PersistedObject{
  private boolean    activeEnabled = true;
  private Path       outFilePath   = null;
  private String     prefix        = "DefaultPrefix";
  private List<Path> inFilePaths   = new ArrayList<>();

  public Settings2(MontoyaApi api, String name, Preferences.Visibility vis){
    super(api, new GsonProvider(), name, vis);
    this.register();
    _loadFromPrefs();
  }

  public boolean activeEnabled(){ return activeEnabled; }
  public boolean activeEnabled(boolean newVal){
    activeEnabled = newVal;
    save();
    return activeEnabled();
  }

  public Path outFilePath(){ return outFilePath; }
  public Path outFilePath(Path newVal){
    outFilePath = newVal;
    save();
    return outFilePath();
  }

  public String prefix(){ return prefix; }
  public String prefix(String newVal){
    prefix = newVal;
    save();
    return prefix();
  }

  public List<Path> inFilePaths(){ return inFilePaths; }
  public List<Path> inFilePaths(List<Path> newLst){
    inFilePaths = newLst;
    save();
    return inFilePaths();
  }
  public List<Path> addInFile(Path newInFilePath){
    inFilePaths.add(newInFilePath);
    save();
    return inFilePaths();
  }
  public List<Path> rmvInFile(int idx){
    inFilePaths.remove(idx);
    save();
    return inFilePaths();
  }
  public List<Path> rmvInFile(Path o){
    inFilePaths.remove(o);
    save();
    return inFilePaths();
  }

  private Settings2(){ super(null, null, null); }

  private void _loadFromPrefs(){
    Settings2 persistedSelf = _prefs.get(_PERSISTED_NAME);
    this.activeEnabled = persistedSelf.activeEnabled;
    this.inFilePaths   = persistedSelf.inFilePaths;
    this.outFilePath   = persistedSelf.outFilePath;
    this.prefix        = persistedSelf.prefix;
  }
}

class ExtensionSettings extends PersistedObject{
  private SubSettings1 subSettings1 = new SubSettings1(this);
  private SubSettings2 subSettings2 = new SubSettings2(this);

  public ExtensionSettings(MontoyaApi api, String name, Preferences.Visibility vis){
    super(api, name, vis);
    this.register();
    _loadFromPrefs();
  }

  public void reset(){
    super.reset();
    _loadFromPrefs();
  }

  public SubSettings1 subSettings1(){ return subSettings1; }
  public SubSettings2 subSettings2(){ return subSettings2; }

  public String toString(){
    String str =
      "subSettings1 = \n" +
      subSettings1.toString() + "\n" +
      "\nsubSettings2 = \n" +
      subSettings2.toString();
    return str;
  }

  private ExtensionSettings(){ super(null, null, null); }

  private void _loadFromPrefs(){
    ExtensionSettings persistedSelf = _prefs.get(_PERSISTED_NAME);
    subSettings1 = new SubSettings1(this, persistedSelf.subSettings1);
    subSettings2 = new SubSettings2(this, persistedSelf.subSettings2);
  }
}

class SubSettings1{
  private boolean enabled = false;

  public SubSettings1(PersistedObject manager){ _manager = manager; }
  public SubSettings1(PersistedObject manager, SubSettings1 source){
    _manager = manager;
    enabled  = source.enabled;
  }

  public void reset(){
    enabled = false;
    _manager.save();
  }

  public boolean enabled(){ return enabled; }
  public boolean enabled(boolean newVal){
    enabled = newVal;
    _manager.save();
    return enabled();
  }

  public String toString(){
    String str = "enabled = " + enabled;
    return str;
  }

  private final transient PersistedObject _manager;
}

class SubSettings2{
  private final SubSubSettings subSubSettings;
  private String str = "something";

  public SubSettings2(PersistedObject manager){
    _manager = manager;
    subSubSettings = new SubSubSettings(_manager);
  }
  public SubSettings2(PersistedObject manager, SubSettings2 source){
    _manager = manager;
    subSubSettings = new SubSubSettings(_manager, source.subSubSettings);
    str = source.str;
  }

  public void reset(){
    subSubSettings.reset();
    str = "something";
    _manager.save();
  }

  public String str(){ return str; }
  public String str(String newStr){
    str = newStr;
    _manager.save();
    return str();
  }

  public SubSubSettings subSubSettings(){ return subSubSettings; }

  public String toString(){
    String str =
      "subSubSettings = \n" +
      subSubSettings.toString() + "\n" +
      "\nstr = " + this.str;
    return str;
  }

  private final transient PersistedObject _manager;
}

class SubSubSettings{
  private boolean enabled = true;

  public SubSubSettings(PersistedObject manager){ _manager = manager; }
  public SubSubSettings(PersistedObject manager, SubSubSettings source){
    _manager = manager;
    enabled  = source.enabled;
  }

  public void reset(){ enabled = true; _manager.save(); }

  public boolean enabled(){ return enabled; }
  public boolean enabled(boolean newVal){
    enabled = newVal;
    _manager.save();
    return enabled();
  }

  public String toString(){
    String str = "enabled = " + enabled;
    return str;
  }

  private final transient PersistedObject _manager;
}