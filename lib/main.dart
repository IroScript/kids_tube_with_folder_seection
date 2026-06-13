import 'dart:io';
import 'dart:async';
import 'dart:typed_data';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:video_player/video_player.dart';
import 'package:file_picker/file_picker.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:path/path.dart' as p;
import 'package:permission_handler/permission_handler.dart';
import 'package:fvp/fvp.dart' as fvp;

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  fvp.registerWith();
  // Setup to support landscape mode
  SystemChrome.setPreferredOrientations([
    DeviceOrientation.landscapeLeft,
    DeviceOrientation.landscapeRight,
    DeviceOrientation.portraitUp,
  ]);
  runApp(const KidsTubeApp());
}

class KidsTubeApp extends StatelessWidget {
  const KidsTubeApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: 'KidsTube Offline',
      theme: ThemeData(
        useMaterial3: true,
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.orangeAccent, primary: Colors.redAccent),
      ),
      home: const KidsTubeHome(),
    );
  }
}

class VideoItem {
  final String path;
  final String title;

  VideoItem({required this.path, required this.title});
}

class KidsTubeHome extends StatefulWidget {
  const KidsTubeHome({super.key});

  @override
  State<KidsTubeHome> createState() => _KidsTubeHomeState();
}

class _KidsTubeHomeState extends State<KidsTubeHome> {
  static const _channel = MethodChannel('com.example.kids_tube/thumbnail');

  List<VideoItem> _videos = [];
  VideoPlayerController? _controller;
  VideoItem? _currentVideo;
  bool _isParentMode = false;
  bool _isLoading = true;
  String? _errorMessage;

  // Full screen control and thumbnail cache variables
  bool _areOverlaysVisible = true;
  Timer? _hideTimer;
  final Map<String, Uint8List?> _thumbnailCache = {};

  @override
  void initState() {
    super.initState();
    _checkPermissionsAndLoad();
  }

  // Check permission and load videos
  Future<void> _checkPermissionsAndLoad() async {
    if (Platform.isAndroid) {
      // Permission.videos for Android 13+ and Permission.storage for lower versions
      Map<Permission, PermissionStatus> statuses = await [
        Permission.storage,
        Permission.videos,
      ].request();
      
      if (statuses[Permission.storage]!.isGranted || statuses[Permission.videos]!.isGranted) {
        _loadSavedVideos();
      } else {
        setState(() {
          _isLoading = false;
          _errorMessage = "Storage permission is required to watch videos. Please grant permission from Settings.";
        });
      }
    } else {
      _loadSavedVideos();
    }
  }

  // Load saved video list
  Future<void> _loadSavedVideos() async {
    final prefs = await SharedPreferences.getInstance();
    final List<String>? savedPaths = prefs.getStringList('kids_videos');
    if (savedPaths != null) {
      setState(() {
        _videos = savedPaths.where((path) => File(path).existsSync()).map((path) => VideoItem(
          path: path,
          title: p.basename(path),
        )).toList();
        _isLoading = false;
      });
      _generateThumbnails();
      if (_videos.isNotEmpty) {
        _playVideo(_videos.first);
      }
    } else {
      setState(() => _isLoading = false);
    }
  }

  Future<void> _saveVideos() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setStringList('kids_videos', _videos.map((v) => v.path).toList());
  }

  // Option to select whole folder
  Future<void> _pickFolder() async {
    String? selectedDirectory = await FilePicker.platform.getDirectoryPath();

    if (selectedDirectory != null) {
      final dir = Directory(selectedDirectory);
      try {
        final List<FileSystemEntity> entities = await dir.list().toList();
        final List<VideoItem> newVideos = [];
        
        for (var entity in entities) {
          if (entity is File) {
            final ext = p.extension(entity.path).toLowerCase();
            // Check common video formats
            if (['.mp4', '.mkv', '.mov', '.avi', '.flv', '.wmv'].contains(ext)) {
              newVideos.add(VideoItem(path: entity.path, title: p.basename(entity.path)));
            }
          }
        }

        if (newVideos.isEmpty) {
          _showToast('No videos found in this folder!');
          return;
        }

        setState(() {
          _videos = newVideos;
          _errorMessage = null;
        });
        _saveVideos();
        _generateThumbnails();
        if (_videos.isNotEmpty) _playVideo(_videos.first);
        _showToast('${newVideos.length} videos added successfully!');
        
      } catch (e) {
        _showToast('Failed to load folder.');
      }
    }
  }

  void _showToast(String msg) {
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(msg), behavior: SnackBarBehavior.floating));
  }

  // Function to play video
  Future<void> _playVideo(VideoItem video) async {
    if (_controller != null) {
      await _controller!.dispose();
      setState(() {
        _controller = null;
      });
    }

    final controller = VideoPlayerController.file(File(video.path));
    
    try {
      await controller.initialize();
      setState(() {
        _currentVideo = video;
        _controller = controller;
        _errorMessage = null;
        _areOverlaysVisible = true; // Show overlay when new video starts playing
      });
      controller.play();
      controller.setLooping(true);
      _startHideTimer(); // Start 3-second timer
    } catch (e) {
      setState(() {
        _errorMessage = "Cannot play the video. It might be an unsupported format or permission issue.\nError: $e";
      });
    }
  }

  void _toggleParentMode() {
    if (!_isParentMode) {
      _showParentLock();
    } else {
      setState(() {
        _isParentMode = false;
      });
      _startHideTimer();
    }
  }

  // Parental control lock
  void _showParentLock() {
    final val1 = 4 + (DateTime.now().second % 5);
    final val2 = 3 + (DateTime.now().millisecond % 4);
    final answer = val1 * val2; 
    final textController = TextEditingController();

    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (context) => AlertDialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(25)),
        title: const Text('Parents Only! 🔐', textAlign: TextAlign.center, style: TextStyle(color: Colors.redAccent, fontWeight: FontWeight.bold)),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Text('Solve the math to unlock:', textAlign: TextAlign.center),
            const SizedBox(height: 15),
            Text('$val1 x $val2 = ?', 
                style: const TextStyle(fontSize: 30, fontWeight: FontWeight.bold, color: Colors.blue)),
            TextField(
              controller: textController,
              keyboardType: TextInputType.number,
              autofocus: true,
              textAlign: TextAlign.center,
              style: const TextStyle(fontSize: 22),
              decoration: const InputDecoration(hintText: 'Enter answer'),
              onSubmitted: (val) => _verifyLock(val, answer, context),
            ),
          ],
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context), child: const Text('Cancel')),
          ElevatedButton(
            onPressed: () => _verifyLock(textController.text, answer, context),
            child: const Text('Unlock'),
          ),
        ],
      ),
    );
  }

  void _verifyLock(String input, int answer, BuildContext context) {
    if (input == answer.toString()) {
      setState(() {
        _isParentMode = true;
        _areOverlaysVisible = true;
      });
      _cancelHideTimer(); // Auto-hide remains disabled in parent mode
      Navigator.pop(context);
    } else {
      _showToast('Wrong answer! Please try again.');
    }
  }

  // Inactivity hide timer
  void _startHideTimer() {
    _cancelHideTimer();
    if (_isParentMode) return; // Will not hide if parental control is open
    _hideTimer = Timer(const Duration(seconds: 3), () {
      if (mounted) {
        setState(() {
          _areOverlaysVisible = false;
        });
      }
    });
  }

  void _cancelHideTimer() {
    _hideTimer?.cancel();
    _hideTimer = null;
  }

  void _onScreenTapped() {
    setState(() {
      _areOverlaysVisible = !_areOverlaysVisible;
    });
    if (_areOverlaysVisible) {
      _startHideTimer();
    } else {
      _cancelHideTimer();
    }
  }

  // Thumbnail generation (using MethodChannel on Android and ffmpeg on Linux)
  Future<void> _generateThumbnails() async {
    for (var video in _videos) {
      if (!_thumbnailCache.containsKey(video.path)) {
        try {
          Uint8List? data;

          // 1. Check if there is an image file (e.g. .jpg, .png) with the same name next to the video file
          final dir = p.dirname(video.path);
          final nameWithoutExt = p.basenameWithoutExtension(video.path);
          final extensions = ['.jpg', '.png', '.webp', '.jpeg', '.PNG', '.JPG', '.JPEG'];
          for (var ext in extensions) {
            final imgFile = File(p.join(dir, '$nameWithoutExt$ext'));
            if (imgFile.existsSync()) {
              data = await imgFile.readAsBytes();
              break;
            }
          }

          // 2. If no local image, try the native or ffmpeg generator
          if (data == null) {
            if (Platform.isAndroid) {
              // Native platform channel for Android
              data = await _channel.invokeMethod<Uint8List>(
                'getVideoThumbnail',
                {'path': video.path},
              );
            } else if (Platform.isLinux) {
              // Generate thumbnail at runtime using ffmpeg for Linux
              final tempDir = Directory.systemTemp;
              final md5Name = video.path.hashCode.toString();
              final outputPath = p.join(tempDir.path, 'thumb_$md5Name.jpg');
              
              final result = await Process.run('ffmpeg', [
                '-y',
                '-i', video.path,
                '-ss', '00:00:01',
                '-vframes', '1',
                '-vf', 'scale=150:-1',
                outputPath,
              ]);
              
              if (result.exitCode == 0 && await File(outputPath).exists()) {
                data = await File(outputPath).readAsBytes();
                try {
                  await File(outputPath).delete();
                } catch (_) {}
              } else {
                debugPrint("ffmpeg failed: ${result.stderr}");
              }
            }
          }

          if (mounted && data != null) {
            setState(() {
              _thumbnailCache[video.path] = data;
            });
          }
        } catch (e) {
          debugPrint("Failed to generate thumbnail for ${video.path}: $e");
        }
      }
    }
  }

  @override
  void dispose() {
    _cancelHideTimer();
    _controller?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    bool isLandscape = MediaQuery.of(context).orientation == Orientation.landscape;

    return Scaffold(
      backgroundColor: Colors.black, // Background is black for video
      body: Stack(
        children: [
          // 1. Full screen video player
          Positioned.fill(
            child: GestureDetector(
              onTap: _onScreenTapped,
              behavior: HitTestBehavior.opaque,
              child: _buildPlayerSection(),
            ),
          ),

          // 2. Play/Pause button in the middle of the screen (when overlay is visible)
          if (_areOverlaysVisible && _controller != null && _controller!.value.isInitialized)
            Center(
              child: IconButton(
                iconSize: 90,
                icon: Icon(
                  _controller!.value.isPlaying ? Icons.pause_circle_filled : Icons.play_circle_filled,
                  color: Colors.white.withOpacity(0.85),
                ),
                onPressed: () {
                  setState(() {
                    if (_controller!.value.isPlaying) {
                      _controller!.pause();
                    } else {
                      _controller!.play();
                    }
                  });
                  _startHideTimer();
                },
              ),
            ),

          // 3. Custom top header (overlay)
          if (_areOverlaysVisible)
            Positioned(
              top: 0, left: 0, right: 0,
              child: SafeArea(
                child: GestureDetector(
                  onTap: () {}, // To prevent overlay from closing
                  child: Container(
                    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                    decoration: BoxDecoration(
                      gradient: LinearGradient(
                        colors: [Colors.black.withOpacity(0.6), Colors.transparent],
                        begin: Alignment.topCenter,
                        end: Alignment.bottomCenter,
                      ),
                    ),
                    child: _buildHeader(),
                  ),
                ),
              ),
            ),

          // 4. Bottom progress bar, video shelf and parent panel (overlay)
          if (_areOverlaysVisible)
            Positioned(
              bottom: 0, left: 0, right: 0,
              child: SafeArea(
                child: GestureDetector(
                  onTap: () {}, // To prevent overlay from closing
                  child: Container(
                    decoration: BoxDecoration(
                      gradient: LinearGradient(
                        colors: [Colors.transparent, Colors.black.withOpacity(0.7)],
                        begin: Alignment.topCenter,
                        end: Alignment.bottomCenter,
                      ),
                    ),
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        if (_controller != null && _controller!.value.isInitialized)
                          Padding(
                            padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 8),
                            child: VideoProgressIndicator(
                              _controller!,
                              allowScrubbing: true,
                              colors: const VideoProgressColors(
                                playedColor: Colors.redAccent,
                                bufferedColor: Colors.white30,
                                backgroundColor: Colors.white12,
                              ),
                            ),
                          ),
                        _buildVideoShelf(isLandscape),
                        if (_isParentMode) _buildParentControls(),
                      ],
                    ),
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }

  Widget _buildHeader() {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 8),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Row(
            children: [
              const Icon(Icons.stars, color: Colors.orange, size: 35),
              const SizedBox(width: 8),
              const Text(
                'KidsTube',
                style: TextStyle(
                  fontSize: 28,
                  fontWeight: FontWeight.w900,
                  color: Colors.white,
                  fontFamily: 'Roboto',
                  letterSpacing: -1,
                  shadows: [Shadow(color: Colors.black45, blurRadius: 4, offset: Offset(0, 2))],
                ),
              ),
            ],
          ),
          IconButton(
            icon: CircleAvatar(
              backgroundColor: _isParentMode ? Colors.green : Colors.white24,
              child: Icon(
                _isParentMode ? Icons.settings : Icons.lock_rounded,
                color: Colors.white,
              ),
            ),
            onPressed: () {
              _toggleParentMode();
              _startHideTimer();
            },
          ),
        ],
      ),
    );
  }

  Widget _buildPlayerSection() {
    return _errorMessage != null
        ? Center(
            child: Padding(
              padding: const EdgeInsets.all(20),
              child: Text(
                _errorMessage!,
                style: const TextStyle(color: Colors.white, fontSize: 16),
                textAlign: TextAlign.center,
              ),
            ),
          )
        : _controller != null && _controller!.value.isInitialized
            ? FittedBox(
                fit: BoxFit.contain, // Fit on screen keeping aspect ratio
                child: SizedBox(
                  width: _controller!.value.size.width,
                  height: _controller!.value.size.height,
                  child: VideoPlayer(_controller!),
                ),
              )
            : Center(
                child: _isLoading
                    ? const CircularProgressIndicator()
                    : const Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Icon(Icons.video_collection_rounded, size: 80, color: Colors.white24),
                          Text('Add a folder with videos!', style: TextStyle(color: Colors.white60)),
                        ],
                      ),
              );
  }

  Widget _buildVideoShelf(bool isLandscape) {
    return Container(
      height: isLandscape ? 160 : 180,
      padding: const EdgeInsets.symmetric(vertical: 10),
      child: _videos.isEmpty
          ? const Center(
              child: Text(
                'Ask parents to add your favorite videos! 🧸',
                style: TextStyle(color: Colors.white70, fontSize: 16),
              ),
            )
          : ListView.builder(
              scrollDirection: Axis.horizontal,
              padding: const EdgeInsets.symmetric(horizontal: 16),
              itemCount: _videos.length,
              itemBuilder: (context, index) {
                final video = _videos[index];
                final isSelected = _currentVideo == video;
                return _buildVideoCard(video, isSelected, index);
              },
            ),
    );
  }

  Widget _buildVideoCard(VideoItem video, bool isSelected, int index) {
    final thumbnailData = _thumbnailCache[video.path];
    final youtubeId = _extractYoutubeId(video.path);

    ImageProvider? imageProvider;
    if (thumbnailData != null) {
      imageProvider = MemoryImage(thumbnailData);
    } else if (youtubeId != null) {
      imageProvider = NetworkImage('https://img.youtube.com/vi/$youtubeId/mqdefault.jpg');
    }

    return GestureDetector(
      onTap: () {
        _playVideo(video);
        _startHideTimer();
      },
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 300),
        width: isSelected ? 220 : 180,
        margin: const EdgeInsets.only(right: 15),
        decoration: BoxDecoration(
          color: isSelected ? Colors.redAccent.withOpacity(0.9) : Colors.white.withOpacity(0.9),
          borderRadius: BorderRadius.circular(25),
          border: Border.all(color: isSelected ? Colors.orange : Colors.transparent, width: 4),
          boxShadow: const [BoxShadow(color: Colors.black26, blurRadius: 8, offset: Offset(0, 4))],
        ),
        child: Column(
          children: [
            Expanded(
              child: Container(
                decoration: BoxDecoration(
                  borderRadius: const BorderRadius.vertical(top: Radius.circular(21)),
                  image: imageProvider != null
                      ? DecorationImage(
                          image: imageProvider,
                          fit: BoxFit.cover,
                        )
                      : null,
                ),
                child: imageProvider == null
                    ? Container(
                        decoration: BoxDecoration(
                          color: Colors.primaries[index % Colors.primaries.length].withOpacity(0.2),
                          borderRadius: const BorderRadius.vertical(top: Radius.circular(21)),
                        ),
                        child: Center(
                          child: Icon(Icons.play_arrow_rounded, size: 60, color: Colors.primaries[index % Colors.primaries.length]),
                        ),
                      )
                    : null,
              ),
            ),
            Padding(
              padding: const EdgeInsets.all(10),
              child: Text(
                video.title,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: TextStyle(
                  fontWeight: FontWeight.bold,
                  fontSize: 14,
                  color: isSelected ? Colors.white : Colors.black87,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildParentControls() {
    return Container(
      margin: const EdgeInsets.all(12),
      padding: const EdgeInsets.all(15),
      decoration: BoxDecoration(
        color: Colors.white.withOpacity(0.95),
        borderRadius: BorderRadius.circular(25),
        boxShadow: const [BoxShadow(color: Colors.black12, blurRadius: 10)],
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceEvenly,
        children: [
          ElevatedButton.icon(
            onPressed: _pickFolder,
            icon: const Icon(Icons.folder_shared_rounded),
            label: const Text('Folder Select'),
          ),
          ElevatedButton.icon(
            onPressed: () {
              setState(() {
                _videos.clear();
                _controller?.dispose();
                _controller = null;
                _currentVideo = null;
              });
              _saveVideos();
            },
            icon: const Icon(Icons.delete_sweep_rounded),
            label: const Text('Clear All'),
            style: ElevatedButton.styleFrom(backgroundColor: Colors.redAccent, foregroundColor: Colors.white),
          ),
          TextButton(
            onPressed: () {
              setState(() {
                _isParentMode = false;
              });
              _startHideTimer();
            },
            child: const Text('Exit'),
          ),
        ],
      ),
    );
  }

  String? _extractYoutubeId(String filePath) {
    final fileName = p.basenameWithoutExtension(filePath);
    final bracketRegExp = RegExp(r'\[([a-zA-Z0-9_-]{11})\]');
    final bracketMatch = bracketRegExp.firstMatch(fileName);
    if (bracketMatch != null) {
      return bracketMatch.group(1);
    }
    final parenRegExp = RegExp(r'\(([a-zA-Z0-9_-]{11})\)');
    final parenMatch = parenRegExp.firstMatch(fileName);
    if (parenMatch != null) {
      return parenMatch.group(1);
    }
    final hyphenRegExp = RegExp(r'-([a-zA-Z0-9_-]{11})$');
    final hyphenMatch = hyphenRegExp.firstMatch(fileName);
    if (hyphenMatch != null) {
      return hyphenMatch.group(1);
    }
    if (fileName.length == 11 && RegExp(r'^[a-zA-Z0-9_-]{11}$').hasMatch(fileName)) {
      return fileName;
    }
    return null;
  }
}
