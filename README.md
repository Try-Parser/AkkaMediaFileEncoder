### Start
```
./server
Command 
Code = 707
```

### Requirements
```
sbt
java
docker
```

### Routes

```
/upload
/file/{UUID}
/codec
/convert
/status/{UUID}
/play/{UUID}
```

### Host Port
```
Http : 8061
Cluster 
	2553 - write model
	2554 - read model
```

### Result Response Example

#### Upload File 
```
$ curl -F ‘file=@path/to/local/file’ http://localhost:8061/upload

```
#### Result
```
{
    "audio": {
        "bit_rate": 128000,
        "channels": 2,
        "decoder": "aac (LC)",
        "quality": 0,
        "sampling_rate": 44100,
        "volume": 0
    },
    "extension": "flv",
    "format": "flv",
    "id": "de1b1f2c-dae5-4426-9f35-f1accf07d9bf",
    "video": {
        "bit_rate": 1000000,
        "decoder": "h264 (High)",
        "frame_rate": 25.0,
        "profile": "BASELINE",
        "size": {
            "height": 360,
            "width": 636
        },
        "tag": ""
    }
}
```
#### Convert File
```
 $curl -i -X POST -H "Content-Type: application/json" -d 'put the json here' http://localhost:8061/convert
```
```
{
    "audio": {
        "bit_rate": 128000,
        "channels": 2,
        "decoder": "mp3",
        "quality": 0,
        "sampling_rate": 44100,
        "volume": 0
    },
    "extension": "mp4",
    "format": "matrosaka",
    "id": "de1b1f2c-dae5-4426-9f35-f1accf07d9bf",
    "video": {
        "bit_rate": 1000000,
        "decoder": "h264",
        "frame_rate": 25.0,
        "profile": "HIGH",
        "size": {
            "height": 360,
            "width": 636
        },
        "tag": ""
    }
}
```
#### Result 
```
{
    "file_name": "595a81ae-bc56-469a-a7e7-9a925d0a251a-1601025045.mp4",
    "id": "7a61cc25-050c-4a37-b4bd-ab6162dcd26b",
    "status": "inprogress"
}
```

#### Get File Result
```
$curl -i -H "Accept: application/json" -H "Content-Type: application/json" -X GET http://localhost:8061/file/{uuid}
```
#### Result 
```
{  
    "journal": {
		"contentType": "",
		"file_id": "d3bf102f-4480-441b-9b90-54511e607c09",
		"file_name": "595a81ae-bc56-469a-a7e7-9a925d0a251a-1601025045.mp4",
		"file_path": "Desktop/file/converted",
		"file_status": 4
	},
	"status":"complete"
}
```
#### Play File TODO
#### Status TODO
