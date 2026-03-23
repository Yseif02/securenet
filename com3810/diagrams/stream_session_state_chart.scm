{
  "graph": {
    "cells": [
      {
        "position": {
          "x": 0,
          "y": 0
        },
        "size": {
          "height": 10,
          "width": 10
        },
        "type": "Statechart",
        "id": "b2c3d4e5-f6a7-8901-bcde-f12345678901",
        "attrs": {
          "name": {
            "text": "stream_session_state_chart Export"
          },
          "specification": {
            "text": "@EventDriven\n@SuperSteps(no)"
          }
        },
        "z": 1
      },
      {
        "position": {
          "x": -560,
          "y": -180
        },
        "size": {
          "height": 110,
          "width": 280
        },
        "type": "State",
        "attrs": {
          "name": {
            "text": "STARTING",
            "fontSize": 11
          },
          "specification": {
            "text": "entry /\nopenIngestionSession;\nstartChunkWaitTimer\n\n// Waiting for first video chunk\n// from IoT Device Firmware"
          }
        },
        "id": "state-stream-starting",
        "z": 5
      },
      {
        "position": {
          "x": -560,
          "y": 10
        },
        "size": {
          "height": 130,
          "width": 280
        },
        "type": "State",
        "attrs": {
          "name": {
            "text": "ACTIVE",
            "fontSize": 11
          },
          "specification": {
            "text": "entry /\nstartChunkTimer;\nselectQualityTier(bandwidth)\n\nvss.chunkReceived /\nwriteSegmentToStorage;\nresetChunkTimer"
          }
        },
        "id": "state-stream-active",
        "z": 6
      },
      {
        "position": {
          "x": -560,
          "y": 220
        },
        "size": {
          "height": 100,
          "width": 280
        },
        "type": "State",
        "attrs": {
          "name": {
            "text": "STOPPING",
            "fontSize": 11
          },
          "specification": {
            "text": "entry /\ndrainRemainingChunks;\nstartDrainTimer\n\n// STREAM_STOP received.\n// Draining buffered chunks."
          }
        },
        "id": "state-stream-stopping",
        "z": 8
      },
      {
        "position": {
          "x": -560,
          "y": 400
        },
        "size": {
          "height": 110,
          "width": 280
        },
        "type": "State",
        "attrs": {
          "name": {
            "text": "FINALIZED",
            "fontSize": 11
          },
          "specification": {
            "text": "entry /\nwriteVideoClipRecord;\ncloseIngestionSession;\nnotifyDmsClipReady"
          }
        },
        "id": "state-stream-finalized",
        "z": 9
      },
      {
        "type": "Transition",
        "attrs": {},
        "source": {
          "id": "state-stream-starting",
          "anchor": {
            "name": "topLeft",
            "args": {
              "dx": "50%",
              "dy": "100%",
              "rotate": true
            }
          },
          "priority": true
        },
        "target": {
          "id": "state-stream-active",
          "anchor": {
            "name": "topLeft",
            "args": {
              "dx": "50%",
              "dy": "0%",
              "rotate": true
            }
          },
          "priority": true
        },
        "connector": {
          "name": "rounded"
        },
        "labels": [
          {
            "attrs": {
              "text": {
                "text": "vss.firstChunkReceived"
              }
            },
            "position": {
              "offset": 10,
              "angle": 0
            }
          },
          {
            "attrs": {
              "label": {
                "text": "1"
              }
            }
          },
          {
            "attrs": {}
          },
          {
            "attrs": {}
          }
        ],
        "id": "trans-starting-active",
        "z": 22,
        "router": {
          "name": "orthogonal"
        },
        "vertices": []
      },
      {
        "type": "Transition",
        "attrs": {},
        "source": {
          "id": "state-stream-active",
          "anchor": {
            "name": "topLeft",
            "args": {
              "dx": "50%",
              "dy": "100%",
              "rotate": true
            }
          },
          "priority": true
        },
        "target": {
          "id": "state-stream-stopping",
          "anchor": {
            "name": "topLeft",
            "args": {
              "dx": "50%",
              "dy": "0%",
              "rotate": true
            }
          },
          "priority": true
        },
        "connector": {
          "name": "rounded"
        },
        "labels": [
          {
            "attrs": {
              "text": {
                "text": "dms.streamStopReceived"
              }
            },
            "position": {
              "offset": 12,
              "angle": 0
            }
          },
          {
            "attrs": {
              "label": {
                "text": "1"
              }
            }
          },
          {
            "attrs": {}
          },
          {
            "attrs": {}
          }
        ],
        "id": "trans-active-stopping",
        "z": 26,
        "router": {
          "name": "orthogonal"
        },
        "vertices": []
      },
      {
        "type": "Transition",
        "attrs": {},
        "source": {
          "id": "state-stream-stopping",
          "anchor": {
            "name": "topLeft",
            "args": {
              "dx": "50%",
              "dy": "100%",
              "rotate": true
            }
          },
          "priority": true
        },
        "target": {
          "id": "state-stream-finalized",
          "anchor": {
            "name": "topLeft",
            "args": {
              "dx": "50%",
              "dy": "0%",
              "rotate": true
            }
          },
          "priority": true
        },
        "connector": {
          "name": "rounded"
        },
        "labels": [
          {
            "attrs": {
              "text": {
                "text": "vss.drainComplete\n[all chunks written]"
              }
            },
            "position": {
              "offset": 12,
              "angle": 0
            }
          },
          {
            "attrs": {
              "label": {
                "text": "1"
              }
            }
          },
          {
            "attrs": {}
          },
          {
            "attrs": {}
          }
        ],
        "id": "trans-stopping-finalized",
        "z": 28,
        "router": {
          "name": "orthogonal"
        },
        "vertices": []
      },
      {
        "position": {
          "x": -488,
          "y": -405
        },
        "size": {
          "height": 90,
          "width": 280
        },
        "type": "State",
        "attrs": {
          "name": {
            "text": "IDLE",
            "fontSize": 11
          },
          "specification": {
            "text": "entry /\nsessionId = null;\ndeviceId = null"
          }
        },
        "id": "state-stream-idle",
        "z": 33
      },
      {
        "type": "Transition",
        "attrs": {},
        "source": {
          "id": "state-stream-finalized",
          "anchor": {
            "name": "topLeft",
            "args": {
              "dx": "0%",
              "dy": "50%",
              "rotate": true
            }
          },
          "priority": true
        },
        "target": {
          "id": "state-stream-idle",
          "anchor": {
            "name": "topLeft",
            "args": {
              "dx": "0%",
              "dy": "80%",
              "rotate": true
            }
          },
          "priority": true
        },
        "connector": {
          "name": "rounded"
        },
        "labels": [
          {
            "attrs": {
              "text": {
                "text": "after 0s"
              }
            },
            "position": {
              "offset": 10,
              "angle": 0
            }
          },
          {
            "attrs": {
              "label": {
                "text": "2"
              }
            }
          },
          {
            "attrs": {}
          },
          {
            "attrs": {}
          }
        ],
        "id": "trans-finalized-idle",
        "z": 34,
        "router": {
          "name": "orthogonal"
        },
        "vertices": [
          {
            "x": -620,
            "y": 455
          },
          {
            "x": -620,
            "y": -295
          }
        ]
      },
      {
        "type": "Transition",
        "attrs": {},
        "source": {
          "id": "state-stream-idle",
          "anchor": {
            "name": "topLeft",
            "args": {
              "dx": "50%",
              "dy": "100%",
              "rotate": true
            }
          },
          "priority": true
        },
        "target": {
          "id": "state-stream-starting",
          "anchor": {
            "name": "topLeft",
            "args": {
              "dx": "50%",
              "dy": "0%",
              "rotate": true
            }
          },
          "priority": true
        },
        "connector": {
          "name": "rounded"
        },
        "labels": [
          {
            "attrs": {
              "text": {
                "text": "dms.streamStartReceived\n/ sessionId = newUUID();\ndeviceId = cmd.deviceId"
              }
            },
            "position": {
              "offset": 12,
              "angle": 0
            }
          },
          {
            "attrs": {
              "label": {
                "text": "1"
              }
            }
          },
          {
            "attrs": {}
          },
          {
            "attrs": {}
          }
        ],
        "id": "trans-idle-starting",
        "z": 34,
        "router": {
          "name": "orthogonal"
        },
        "vertices": []
      },
      {
        "position": {
          "x": -667,
          "y": -375
        },
        "size": {
          "height": 18,
          "width": 18
        },
        "type": "Entry",
        "entryKind": "Initial",
        "attrs": {},
        "id": "init-node-stream",
        "z": 35,
        "embeds": [
          "init-label-stream"
        ]
      },
      {
        "type": "NodeLabel",
        "label": true,
        "size": {
          "width": 15,
          "height": 15
        },
        "position": {
          "x": -667,
          "y": -360
        },
        "attrs": {
          "label": {
            "refX": "50%",
            "textAnchor": "middle",
            "refY": "50%",
            "textVerticalAnchor": "middle"
          }
        },
        "id": "init-label-stream",
        "z": 36,
        "parent": "init-node-stream"
      },
      {
        "type": "Transition",
        "attrs": {},
        "source": {
          "id": "init-node-stream"
        },
        "target": {
          "id": "state-stream-idle",
          "anchor": {
            "name": "topLeft",
            "args": {
              "dx": "2%",
              "dy": "50%",
              "rotate": true
            }
          },
          "priority": true
        },
        "connector": {
          "name": "rounded"
        },
        "labels": [
          {
            "attrs": {},
            "position": {}
          },
          {
            "attrs": {
              "label": {
                "text": "1"
              }
            }
          },
          {
            "attrs": {}
          },
          {
            "attrs": {}
          }
        ],
        "id": "trans-init-idle",
        "z": 37,
        "router": {
          "name": "orthogonal"
        },
        "vertices": []
      },
      {
        "position": {
          "x": -7,
          "y": 437
        },
        "size": {
          "height": 110,
          "width": 280
        },
        "type": "State",
        "attrs": {
          "name": {
            "text": "FAILED",
            "fontSize": 11
          },
          "specification": {
            "text": "entry /\nfinalizePartialClip;\nlogStreamFailure;\nnotifyDmsStreamLost"
          }
        },
        "id": "state-stream-failed",
        "z": 65
      },
      {
        "type": "Transition",
        "attrs": {},
        "source": {
          "id": "state-stream-failed",
          "anchor": {
            "name": "topLeft",
            "args": {
              "dx": "0.35%",
              "dy": "54.587%",
              "rotate": true
            }
          },
          "priority": true
        },
        "target": {
          "id": "state-stream-idle",
          "anchor": {
            "name": "topLeft",
            "args": {
              "dx": "100%",
              "dy": "50%",
              "rotate": true
            }
          },
          "priority": true
        },
        "connector": {
          "name": "rounded"
        },
        "labels": [
          {
            "attrs": {
              "text": {
                "text": "vss.partialClipFinalized\nafter 0s"
              }
            },
            "position": {
              "distance": 0.1930803005222011,
              "offset": 8,
              "angle": 0
            }
          },
          {
            "attrs": {
              "label": {
                "text": "1"
              }
            }
          },
          {
            "attrs": {}
          },
          {
            "attrs": {}
          }
        ],
        "id": "trans-failed-idle",
        "z": 66,
        "router": {
          "name": "orthogonal"
        },
        "vertices": [
          {
            "x": -110,
            "y": 482.05
          },
          {
            "x": -110,
            "y": -245
          }
        ]
      },
      {
        "type": "Transition",
        "attrs": {},
        "source": {
          "id": "state-stream-starting"
        },
        "target": {
          "id": "state-stream-failed",
          "anchor": {
            "name": "topLeft",
            "args": {
              "dx": "96.648%",
              "dy": "71.101%",
              "rotate": true
            }
          },
          "priority": true
        },
        "connector": {
          "name": "rounded"
        },
        "labels": [
          {
            "attrs": {
              "text": {
                "text": "vss.chunkWaitExpired\n[no chunks received]"
              }
            },
            "position": {
              "distance": 0.48832242856602215,
              "offset": -27.07501220703125,
              "angle": 0
            }
          },
          {
            "attrs": {
              "label": {
                "text": "2"
              }
            }
          },
          {
            "attrs": {}
          },
          {
            "attrs": {}
          }
        ],
        "id": "e9962583-cfe6-4b16-922c-d3c602ffcd23",
        "z": 68,
        "router": {
          "name": "orthogonal"
        },
        "vertices": [
          {
            "x": -526,
            "y": -459
          },
          {
            "x": -11,
            "y": -297
          },
          {
            "x": 349,
            "y": -297
          },
          {
            "x": 349,
            "y": 468
          },
          {
            "x": 350,
            "y": 468
          }
        ]
      },
      {
        "position": {
          "x": -52,
          "y": -205
        },
        "size": {
          "height": 130,
          "width": 280
        },
        "type": "State",
        "attrs": {
          "name": {
            "text": "DEGRADED",
            "fontSize": 11
          },
          "specification": {
            "text": "entry /\nselectLowerQualityTier(bandwidth)\n\nvss.chunkReceived /\nwriteSegmentToStorage;\nresetChunkTimer"
          }
        },
        "id": "state-stream-degraded",
        "z": 74
      },
      {
        "type": "Transition",
        "attrs": {},
        "source": {
          "id": "state-stream-active",
          "anchor": {
            "name": "topLeft",
            "args": {
              "dx": "100%",
              "dy": "40%",
              "rotate": true
            }
          },
          "priority": true
        },
        "target": {
          "id": "state-stream-degraded",
          "anchor": {
            "name": "topLeft",
            "args": {
              "dx": "3.852%",
              "dy": "71.654%",
              "rotate": true
            }
          },
          "priority": true
        },
        "connector": {
          "name": "rounded"
        },
        "labels": [
          {
            "attrs": {
              "text": {
                "text": "vss.bandwidthLow\n[bandwidth < sdThresholdKbps]"
              }
            },
            "position": {
              "distance": 0.32710973743793925,
              "offset": -22,
              "angle": 0
            }
          },
          {
            "attrs": {
              "label": {
                "text": "1"
              }
            }
          },
          {
            "attrs": {}
          },
          {
            "attrs": {}
          }
        ],
        "id": "trans-active-degraded",
        "z": 75,
        "router": {
          "name": "orthogonal"
        },
        "vertices": [
          {
            "x": -238,
            "y": 62
          },
          {
            "x": -238,
            "y": -111.85
          }
        ]
      },
      {
        "type": "Transition",
        "attrs": {},
        "source": {
          "id": "state-stream-degraded",
          "anchor": {
            "name": "topLeft",
            "args": {
              "dx": "67.934%",
              "dy": "95.748%",
              "rotate": true
            }
          },
          "priority": true
        },
        "target": {
          "id": "state-stream-failed",
          "anchor": {
            "name": "topLeft",
            "args": {
              "dx": "80.19%",
              "dy": "19.908%",
              "rotate": true
            }
          },
          "priority": true
        },
        "connector": {
          "name": "rounded"
        },
        "labels": [
          {
            "attrs": {
              "text": {
                "text": "vss.chunkTimerExpired"
              }
            },
            "position": {
              "distance": 0.8438433945194159,
              "offset": 2.919542202940093,
              "angle": 0
            }
          },
          {
            "attrs": {
              "label": {
                "text": "3"
              }
            }
          },
          {
            "attrs": {}
          },
          {
            "attrs": {}
          }
        ],
        "id": "trans-degraded-failed",
        "z": 75,
        "router": {
          "name": "orthogonal"
        },
        "vertices": [
          {
            "x": 208.22,
            "y": 414
          }
        ]
      },
      {
        "type": "Transition",
        "attrs": {},
        "source": {
          "id": "state-stream-degraded",
          "anchor": {
            "name": "topLeft",
            "args": {
              "dx": "24.162%",
              "dy": "99.291%",
              "rotate": true
            }
          },
          "priority": true
        },
        "target": {
          "id": "state-stream-active",
          "anchor": {
            "name": "topLeft",
            "args": {
              "dx": "100%",
              "dy": "60%",
              "rotate": true
            }
          },
          "priority": true
        },
        "connector": {
          "name": "rounded"
        },
        "labels": [
          {
            "attrs": {
              "text": {
                "text": "vss.bandwidthRestored\n[bandwidth >= hdThresholdKbps]"
              }
            },
            "position": {
              "distance": 0.0855244566858672,
              "offset": 3.07863777056212,
              "angle": 0
            }
          },
          {
            "attrs": {
              "label": {
                "text": "2"
              }
            }
          },
          {
            "attrs": {}
          },
          {
            "attrs": {}
          }
        ],
        "id": "trans-degraded-active",
        "z": 75,
        "router": {
          "name": "orthogonal"
        },
        "vertices": [
          {
            "x": 15.65,
            "y": 88
          }
        ]
      },
      {
        "type": "Transition",
        "attrs": {},
        "source": {
          "id": "state-stream-degraded",
          "anchor": {
            "name": "topLeft",
            "args": {
              "dx": "50%",
              "dy": "100%",
              "rotate": true
            }
          },
          "priority": true
        },
        "target": {
          "id": "state-stream-stopping",
          "anchor": {
            "name": "topLeft",
            "args": {
              "dx": "100%",
              "dy": "40%",
              "rotate": true
            }
          },
          "priority": true
        },
        "connector": {
          "name": "rounded"
        },
        "labels": [
          {
            "attrs": {
              "text": {
                "text": "dms.streamStopReceived"
              }
            },
            "position": {
              "distance": 0.8168885150861998,
              "offset": 11.559646606445312,
              "angle": 0
            }
          },
          {
            "attrs": {
              "label": {
                "text": "1"
              }
            }
          },
          {
            "attrs": {}
          },
          {
            "attrs": {}
          }
        ],
        "id": "trans-degraded-stopping",
        "z": 75,
        "router": {
          "name": "orthogonal"
        },
        "vertices": [
          {
            "x": -16,
            "y": 272
          }
        ]
      },
      {
        "type": "Transition",
        "attrs": {},
        "source": {
          "id": "state-stream-active",
          "anchor": {
            "name": "topLeft",
            "args": {
              "dx": "100%",
              "dy": "80%",
              "rotate": true
            }
          },
          "priority": true
        },
        "target": {
          "id": "state-stream-failed",
          "anchor": {
            "name": "topLeft",
            "args": {
              "dx": "10.505%",
              "dy": "10%",
              "rotate": true
            }
          },
          "priority": true
        },
        "connector": {
          "name": "rounded"
        },
        "labels": [
          {
            "attrs": {
              "text": {
                "text": "vss.chunkTimerExpired\n[no chunk received unexpectedly]"
              }
            },
            "position": {
              "distance": 0.34859501231478157,
              "offset": -19,
              "angle": 0
            }
          },
          {
            "attrs": {
              "label": {
                "text": "3"
              }
            }
          },
          {
            "attrs": {}
          },
          {
            "attrs": {}
          }
        ],
        "id": "trans-active-failed",
        "z": 76,
        "router": {
          "name": "orthogonal"
        },
        "vertices": [
          {
            "x": -21,
            "y": 171
          }
        ]
      }
    ]
  },
  "genModel": {
    "generator": {
      "type": "create::c",
      "features": {
        "Outlet": {
          "targetProject": "",
          "targetFolder": "",
          "libraryTargetFolder": "",
          "skipLibraryFiles": "",
          "apiTargetFolder": ""
        },
        "LicenseHeader": {
          "licenseText": ""
        },
        "FunctionInlining": {
          "inlineReactions": false,
          "inlineEntryActions": false,
          "inlineExitActions": false,
          "inlineEnterSequences": false,
          "inlineExitSequences": false,
          "inlineChoices": false,
          "inlineEnterRegion": false,
          "inlineExitRegion": false,
          "inlineEntries": false
        },
        "OutEventAPI": {
          "observables": false,
          "getters": false
        },
        "IdentifierSettings": {
          "moduleName": "Test",
          "statemachinePrefix": "test",
          "separator": "_",
          "headerFilenameExtension": "h",
          "sourceFilenameExtension": "c"
        },
        "Tracing": {
          "enterState": false,
          "exitState": false,
          "generic": false
        },
        "Includes": {
          "useRelativePaths": false,
          "generateAllSpecifiedIncludes": false
        },
        "GeneratorOptions": {
          "userAllocatedQueue": false,
          "metaSource": false
        },
        "GeneralFeatures": {
          "timerService": false,
          "timerServiceTimeType": ""
        },
        "Debug": {
          "dumpSexec": false
        }
      }
    }
  }
}