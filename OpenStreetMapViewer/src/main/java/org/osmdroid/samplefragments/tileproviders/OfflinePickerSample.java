package org.osmdroid.samplefragments.tileproviders;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;

import com.github.angads25.filepicker.controller.DialogSelectionListener;
import com.github.angads25.filepicker.model.DialogConfigs;
import com.github.angads25.filepicker.model.DialogProperties;
import com.github.angads25.filepicker.view.FilePickerDialog;

import org.mapsforge.map.android.rendertheme.AssetsRenderTheme;
import org.mapsforge.map.rendertheme.XmlRenderTheme;
import org.osmdroid.R;
import org.osmdroid.gpkg.GeoPackageMapTileModuleProvider;
import org.osmdroid.gpkg.GeoPackageProvider;
import org.osmdroid.mapsforge.MapsForgeTileModuleProvider;
import org.osmdroid.mapsforge.MapsForgeTileProvider;
import org.osmdroid.mapsforge.MapsForgeTileSource;
import org.osmdroid.samplefragments.BaseSampleFragment;
import org.osmdroid.tileprovider.MapTileProviderArray;
import org.osmdroid.tileprovider.modules.ArchiveFileFactory;
import org.osmdroid.tileprovider.modules.IArchiveFile;
import org.osmdroid.tileprovider.modules.IFilesystemCache;
import org.osmdroid.tileprovider.modules.MapTileApproximater;
import org.osmdroid.tileprovider.modules.MapTileAssetsProvider;
import org.osmdroid.tileprovider.modules.MapTileFileArchiveProvider;
import org.osmdroid.tileprovider.modules.MapTileFilesystemProvider;
import org.osmdroid.tileprovider.modules.MapTileModuleProviderBase;
import org.osmdroid.tileprovider.modules.SqlTileWriter;
import org.osmdroid.tileprovider.modules.TileWriter;
import org.osmdroid.tileprovider.tilesource.FileBasedTileSource;
import org.osmdroid.tileprovider.tilesource.ITileSource;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.tileprovider.util.SimpleRegisterReceiver;
import org.osmdroid.views.MapView;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * lets you pick one or more offline tile archives/providers
 * then a named tile source which will have tiles in at least one archive or provider
 * created on 8/20/2017.
 *
 * @author Alex O'Ree
 */

public class OfflinePickerSample extends BaseSampleFragment implements View.OnClickListener {

    private Button btnArchives;
    private Button btnSource;
    private Set<ITileSource> tileSources = new HashSet<>();

    @Override
    public String getSampleTitle() {
        return "Offline Only Tiles with picker";
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View root = inflater.inflate(R.layout.sample_map_two_button, container, false);

        mMapView = (MapView) root.findViewById(R.id.mapview);
        btnArchives = (Button) root.findViewById(R.id.button1);
        btnArchives.setOnClickListener(this);
        btnArchives.setText("Pick Files");

        btnSource = (Button) root.findViewById(R.id.button2);
        btnSource.setOnClickListener(this);
        btnSource.setText("Pick Tile Source");
        return root;
    }

    @Override
    public void addOverlays() {
        //not even needed since we are using the offline tile provider only
        this.mMapView.setUseDataConnection(true);
    }

    /**
     * step 1, users selects files
     */
    private void promptForFiles() {
        DialogProperties properties = new DialogProperties();
        properties.selection_mode = DialogConfigs.MULTI_MODE;
        properties.selection_type = DialogConfigs.FILE_SELECT;
        properties.root = new File(DialogConfigs.DEFAULT_DIR);
        ;//(Configuration.getInstance().getOsmdroidBasePath());
        properties.error_dir = new File(DialogConfigs.DEFAULT_DIR);
        properties.offset = new File(DialogConfigs.DEFAULT_DIR);

        Set<String> registeredExtensions = ArchiveFileFactory.getRegisteredExtensions();
        //api check
        if (Build.VERSION.SDK_INT >= 14)
            registeredExtensions.add("gpkg");
        if (Build.VERSION.SDK_INT >= 10)
            registeredExtensions.add("map");


        String[] ret = new String[registeredExtensions.size()];
        ret = registeredExtensions.toArray(ret);
        properties.extensions = ret;

        FilePickerDialog dialog = new FilePickerDialog(getContext(), properties);
        dialog.setTitle("Select a File");
        dialog.setDialogSelectionListener(new DialogSelectionListener() {
            @Override
            public void onSelectedFilePaths(String[] files) {
                //files is the array of the paths of files selected by the Application User.
                setProviderConfig(files);
            }

        });
        dialog.show();
    }


    /**
     * step two, configure our offline tile provider
     * @param files
     */
    private void setProviderConfig(String[] files) {
        if (files == null || files.length == 0)
            return;
        SimpleRegisterReceiver simpleRegisterReceiver = new SimpleRegisterReceiver(getContext());

        IFilesystemCache tileWriter;

        if (Build.VERSION.SDK_INT < 10) {
            tileWriter = new TileWriter();
        } else {
            tileWriter = new SqlTileWriter();
        }

        tileSources.clear();
        List<MapTileModuleProviderBase> providers = new ArrayList<>();
        providers.add(new MapTileAssetsProvider(simpleRegisterReceiver, getContext().getAssets()));

        List<File> geopackages = new ArrayList<>();
        List<File> forgeMaps = new ArrayList<>();
        List<IArchiveFile> archives = new ArrayList<>();
        //this part seperates the geopackage and maps forge stuff since they are handled differently
        for (int i = 0; i < files.length; i++) {
            File archive = new File(files[i]);
            if (archive.getName().endsWith("gpkg")) {
                geopackages.add(archive);
            } else if (archive.getName().endsWith("map")) {
                forgeMaps.add(archive);
            } else {
                IArchiveFile temp = ArchiveFileFactory.getArchiveFile(archive);
                if (temp != null) {
                    Set<String> tileSources = temp.getTileSources();
                    Iterator<String> iterator = tileSources.iterator();
                    while (iterator.hasNext()) {

                        this.tileSources.add(FileBasedTileSource.getSource(iterator.next()));
                        archives.add(temp);
                    }
                }

            }
        }

        //setup the standard osmdroid-android library supported offline tile providers
        IArchiveFile[] archArray = new IArchiveFile[archives.size()];
        archArray = archives.toArray(archArray);
        final MapTileFileArchiveProvider mapTileFileArchiveProvider = new MapTileFileArchiveProvider(simpleRegisterReceiver, TileSourceFactory.DEFAULT_TILE_SOURCE, archArray);


        GeoPackageMapTileModuleProvider geopackage = null;
        GeoPackageProvider provider = null;
        //geopackages
        if (!geopackages.isEmpty()) {
            File[] maps = new File[geopackages.size()];
            maps = geopackages.toArray(maps);

            if (Build.VERSION.SDK_INT < 10) {

                provider = new GeoPackageProvider(maps, getContext());
                geopackage = provider.geoPackageMapTileModuleProvider();
                providers.add(geopackage);
                List<GeoPackageMapTileModuleProvider.Container> geotileSources = geopackage.getTileSources();
                for (int i = 0; i < geotileSources.size(); i++) {
                    //this is a list of geopackages, since we only support tile tables, pick the first one of those
                    //ideally this should populate a spinner so that the user can select whatever tile source they want
                    if (geotileSources.get(i) != null && !geotileSources.get(i).tiles.isEmpty()) {
                        for (int k = 0; k < geotileSources.get(i).tiles.size(); k++) {
                            XYTileSource currentSource = (XYTileSource) provider.getTileSource(geotileSources.get(i).database,
                                geotileSources.get(i).tiles.get(k)
                            );
                            tileSources.add(currentSource);
                        }
                    }
                }
            }
        }

        MapsForgeTileModuleProvider moduleProvider=null;
        if (!forgeMaps.isEmpty()) {
            //fire up the forge maps...
            XmlRenderTheme theme = null;
            try {
                theme = new AssetsRenderTheme(getContext().getApplicationContext(), "renderthemes/", "rendertheme-v4.xml");
            } catch (Exception ex) {
                ex.printStackTrace();
            }

            File[] forge = new File[forgeMaps.size()];
            forge = forgeMaps.toArray(forge);
            MapsForgeTileSource fromFiles = MapsForgeTileSource.createFromFiles(forge, theme, "rendertheme-v4");
            tileSources.add(fromFiles);
            // Create the module provider; this class provides a TileLoader that
            // actually loads the tile from the map file.
            moduleProvider = new MapsForgeTileModuleProvider(simpleRegisterReceiver, fromFiles, tileWriter);


        }


        final MapTileApproximater approximationProvider = new MapTileApproximater();
        approximationProvider.addProvider(mapTileFileArchiveProvider);

        if (geopackage != null) {
            providers.add(geopackage);
            approximationProvider.addProvider(geopackage);
        }
        if (moduleProvider!=null) {
            providers.add(moduleProvider);
            approximationProvider.addProvider(moduleProvider);
        }

        providers.add(mapTileFileArchiveProvider);
        providers.add(approximationProvider);
        MapTileModuleProviderBase[] providerArray = new MapTileModuleProviderBase[providers.size()];
        for (int i = 0; i < providers.size(); i++) {
            providerArray[i] = providers.get(i);
        }


        MapTileProviderArray obj = new MapTileProviderArray(TileSourceFactory.DEFAULT_TILE_SOURCE, simpleRegisterReceiver, providerArray);
        mMapView.setTileProvider(obj);

        //ok everything is setup, we now have 0 or many tile sources available, ask the user
        promptForTileSource();
    }

    /**
     * step 3 ask for the tile source
     */
    private void promptForTileSource() {
        AlertDialog.Builder builderSingle = new AlertDialog.Builder(getContext());
        builderSingle.setIcon(R.drawable.icon);
        builderSingle.setTitle("Select Offline Tile source:-");

        final ArrayAdapter<ITileSource> arrayAdapter = new ArrayAdapter<ITileSource>(getContext(), android.R.layout.select_dialog_singlechoice);
        arrayAdapter.addAll(tileSources);

        builderSingle.setNegativeButton("cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        builderSingle.setAdapter(arrayAdapter, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                final ITileSource strName = arrayAdapter.getItem(which);
                AlertDialog.Builder builderInner = new AlertDialog.Builder(getContext());
                builderInner.setMessage(strName.name());
                builderInner.setTitle("Your Selected Item is");
                builderInner.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                        mMapView.setTileSource(strName);//new XYTileSource(strName, 0, 22, 256, "png", new String[0]));
                        //on tile sources that are supported, center the map an area that's within bounds
                        if (strName instanceof MapsForgeTileSource) {
                            MapsForgeTileSource src = (MapsForgeTileSource) strName;
                            mMapView.getController().setZoom(src.getMinimumZoomLevel());
                            mMapView.zoomToBoundingBox(src.getBoundsOsmdroid(), true);
                        } else if (strName instanceof GeoPackageProvider.TileSourceBounds) {
                            GeoPackageProvider.TileSourceBounds src = (GeoPackageProvider.TileSourceBounds) strName;
                            mMapView.zoomToBoundingBox(src.bounds, true);
                            mMapView.getController().setZoom(src.minzoom);
                        }

                        dialog.dismiss();
                    }
                });
                builderInner.show();
            }
        });
        builderSingle.show();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.button1:
                //pick files
                promptForFiles();
                break;

            case R.id.button2:
                //pick source
                promptForTileSource();
                break;
        }

    }
}
