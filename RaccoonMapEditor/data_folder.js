// data_folder.js - the game's data/ folder, opened through the File System Access API
// (Chrome / Edge). The folder is picked again every time the editor opens.
//
// Textures come from data/tex/, sprites from data/sprites/, behaviour scripts from
// data/scripts/, and maps are read from and saved to data/maps/.

const REQUIRED_FOLDERS = ['tex', 'sprites', 'scripts', 'maps'];

let dataFolder = null;      // FileSystemDirectoryHandle for data/
let subFolders = {};        // 'tex' -> FileSystemDirectoryHandle, and so on
let textureNames = [];      // every .png in tex/, sorted
let spriteNames = [];       // every .png in sprites/, sorted
let scriptNames = [];       // every .lua in scripts/, sorted

// 'tex/wall.png' or 'sprites/cat.png' -> { img, failed }. img stays null until the file has loaded.
const images = new Map();

function folderApiAvailable() {
    return typeof window.showDirectoryPicker === 'function';
}

// Asks for the data/ folder. Returns a list of warnings (may be empty).
// Throws an Error saying what is wrong when the folder can't be used.
async function pickDataFolder() {
    const handle = await window.showDirectoryPicker({ mode: 'readwrite' });

    const found = {};
    const missing = [];
    for (const name of REQUIRED_FOLDERS) {
        try {
            found[name] = await handle.getDirectoryHandle(name);
        } catch (e) {
            missing.push(name + '/');
        }
    }
    if (missing.length > 0) {
        throw new Error('"' + handle.name + '" is missing ' + missing.join(', ')
            + '. Pick the game\'s data folder (it has tex, sprites, scripts and maps inside).');
    }

    dataFolder = handle;
    subFolders = found;
    images.clear();
    textureNames = await listFiles(subFolders.tex, '.png');
    spriteNames = await listFiles(subFolders.sprites, '.png');
    scriptNames = await listFiles(subFolders.scripts, '.lua');

    const warnings = [];
    if (!textureNames.includes(DEFAULT_TEXTURE)) {
        warnings.push('tex/' + DEFAULT_TEXTURE + ' is missing. New cells use it, so they will show the missing-texture checker.');
    }
    return warnings;
}

// Names of the files in a folder that end with the extension, sorted.
async function listFiles(folderHandle, extension) {
    const names = [];
    for await (const [name, entry] of folderHandle.entries()) {
        if (entry.kind === 'file' && name.toLowerCase().endsWith(extension)) {
            names.push(name);
        }
    }
    names.sort((a, b) => a.localeCompare(b));
    return names;
}

async function listMapFiles() {
    return listFiles(subFolders.maps, '.rmap');
}

// ============================================================== images

function getTexture(name) {
    return getImage('tex', name);
}

function getSpriteImage(name) {
    return getImage('sprites', name);
}

// Returns the image's entry, starting the load the first time it is asked for.
// When the image arrives, the map and the thumbnails are redrawn.
function getImage(folder, name) {
    const key = folder + '/' + name;
    let entry = images.get(key);
    if (entry === undefined) {
        entry = { img: null, failed: false };
        images.set(key, entry);
        loadImage(folder, name, entry);
    }
    return entry;
}

async function loadImage(folder, name, entry) {
    try {
        const fileHandle = await subFolders[folder].getFileHandle(name);
        const file = await fileHandle.getFile();
        const img = new Image();
        img.onload = () => {
            entry.img = img;
            requestDraw();
            refreshThumbnails();
        };
        img.onerror = () => {
            entry.failed = true;
            requestDraw();
            refreshThumbnails();
        };
        img.src = URL.createObjectURL(file);
    } catch (e) {
        // Not in the folder: drawn as the missing-texture checker, like the engine does.
        entry.failed = true;
        requestDraw();
        refreshThumbnails();
    }
}

// =============================================================== maps

async function readMapFile(name) {
    const fileHandle = await subFolders.maps.getFileHandle(name + '.rmap');
    const file = await fileHandle.getFile();
    return new Uint8Array(await file.arrayBuffer());
}

async function mapFileExists(name) {
    try {
        await subFolders.maps.getFileHandle(name + '.rmap');
        return true;
    } catch (e) {
        return false;
    }
}

async function writeMapFile(name, bytes) {
    const fileHandle = await subFolders.maps.getFileHandle(name + '.rmap', { create: true });
    const writable = await fileHandle.createWritable();
    await writable.write(bytes);
    await writable.close();
}
