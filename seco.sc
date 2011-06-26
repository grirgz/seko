(
var make_widget_list_view, make_header_button;

"/home/ggz/code/sc/seco/keycode.sc".loadDocument;
"/home/ggz/code/sc/seco/editplayer1.sc".loadDocument;

~toggle_value = { arg value;
	if( value.isNil, { value = 0 });
	((value+1) % 2);
};

~toggle_button = { arg button;
	button.value = ((button.value+1) % 2);
};

~matrix3_from_list = { arg list, collectfun = { arg x; x };
	var banklist = List[], collist = List[], celllist = List[];
	var bankidx = 0, colidx = 0, cellidx = 0;
	list.do { arg asso;
		if( cellidx >= 4, {
			if( colidx >= 8, {
				banklist.add( collist );
				collist = List[];
				colidx = 0;
				bankidx = bankidx + 1;
			});
			collist.add( celllist );
			colidx = colidx + 1;
			cellidx = 0;
			celllist = List[];
		});
		celllist.add( collectfun.(asso) );
		cellidx = cellidx + 1;
	};
	banklist.add( collist );
	collist.add( celllist );
	banklist;

};


~make_cell_button = { arg parent, label, action;
	var bt;

	bt = GUI.button.new(parent, Rect(50,50,50,50));
	bt.states = [
		[ " " ++ label ++ " ", Color.black, Color.white],
		[ "=" ++ label ++ "=", Color.white, Color.black ]
	];
	bt.value = 0;

	bt.action = action
};

~pedynscalar = { arg data, key, repeat = 100;
	Prout({
		repeat.do {
			currentEnvironment[data][key].yield;
		};
	});
};

~pdynarray = { arg fun, repeat=100000;
	Prout({
		var idx;
		var val = 0;
		repeat.do {
			idx = 0;
			val = fun.(idx);
			//[val, idx].debug("pdynarray val idx");
			while( { val.notNil } , { 
				val.yield;
				idx = idx + 1;
				val = fun.(idx);
			});
		}
	})
};

// ==========================================
// PLAYER FACTORY
// ==========================================

~make_event_key_reader = { arg argName, self;
	switch(argName, 
		\stepline, { 
			~pdynarray.( { arg idx; self.self.get_arg(argName)[idx] } );
		},
		\type, {
			Pif( Pkey(\stepline) > 0 , \note, \rest) // WTF with == ?????
		},
		//default:
		{
			//self.data[argName] = PatternProxy.new;
			Prout({
				var repeat = 100000;
				var argdata = self.get_arg(argName);
				var idx, val=0;
				repeat.do {
					switch( argdata.current_kind,
						\scalar, {
							argdata.scalar.val.yield;
						},
						\seq, {
							idx = 0;
							val = argdata.seq.val[idx];
							while( { val.notNil } , { 
								val.yield;
								idx = idx + 1;
								val = argdata.seq.val[idx];
							});
						}
					);
				}
			})
		}
	);
};

~player_get_arg = { arg self, argName;
	var ret;
	argName.dump;
	//self.get_args.do { arg an; an.debug("an====").dump };
	ret = if(self.get_args.includes(argName), {
		if([\type, \stepline].includes(argName), {
			self.data[argName];
		}, {
			//self.data[argName].source;
			self.data[argName];
		})
	}, {
		("ERROR: player: no such arg: " ++ argName ++ "!" ++ self).postln;
		nil;
	});
	//ret.debug("get_arg ret");
	ret;
};

~player_set_arg = { arg self, argName, val;
	if([\type, \stepline].includes(argName), {
		self.data[argName] = val;
	}, {
		//self.data[argName].source = val;
		self.data[argName] = val;
	})
};

~get_spec = { arg argName, defname=nil, default_spec=\widefreq;
	var spec;
	if( argName.asSpec.notNil, {
		argName.asSpec;
	}, {
		spec = default_spec.asSpec;
		try { 
			spec = SynthDescLib.global.synthDescs[defname].metadata.specs[argName].asSpec;
		};
		spec;
	});
};

~make_literal_param = { arg name, val;
	(
		name: name,
		classtype: \literal,
		get_val: val,

		refresh: { arg self;
			self.changed(\selected);
		},
		vpattern: val
	);
};

~make_stepline_param = { arg name, default_value;
	var ret;
	ret = (
		name: name,
		classtype: \stepline,
		selected_cell: 0,
		selected: 0,
		default_val: default_value.asList,
		seq: (
			val: default_value.asList,
			change: { arg self, fun;
				self.val = fun.(self.val);
			}
		),
		get_cells: { arg self;
			self.seq.val;
		},
		get_selected_cell: { arg self;
			self.selected_cell;
		},
		select_param: { arg self;
			self.selected = 1;
			self.changed(\selected);
		},
		deselect_param: { arg self;
			self.selected = 0;
			self.changed(\selected);
		},

		add_cells: { arg self, cells;
			self.seq.val.addAll(cells);
			self.changed(\cells);
		},

		remove_cells: { arg self, num;
			self.seq.val = self.seq.val[.. (self.seq.val.size - num - 1) ];
			self.changed(\cells);
		},

		set_val: { arg self, val;
			self.seq_val[ self.get_selected_cell.() ] = if(val > 1, { 1 },{ 0 });
		},

		tick: { arg idx; "TICK!".postln; },

		toggle_cell: { arg self, idx;
			var oldsel;
			[idx, self.get_cells].debug("make_control_param.select_cell idx, selg.get_cells");
			if( idx < self.get_cells.size, {
				//oldsel = self.selected_cell;
				self.selected_cell = idx;
				//self.changed(\selected_cell, oldsel);
				self.seq.val[ idx ] = ~toggle_value.(self.seq.val[ idx ]);
				self.changed(\val, self.selected_cell);
			})
		},
		refresh: { arg self;
			self.changed(\cells);
		},
		vpattern: { arg self; ~pdynarray.( { arg idx; self.tick(idx); self.seq.val[idx] } ) };
	);
	ret;
};

~make_type_param = { arg name;
	var ret;
	ret = (
		name: name,
		classtype: \type,
		refresh: { arg self; },
		vpattern: { arg self; Pif( Pkey(\stepline) > 0 , \note, \rest) } // WTF with == ?????
	);
	ret;
};

~make_control_param = { arg name, kind, default_value, spec;
	var ret;
	var bar_length = 4;

	ret = (
		name: name,
		classtype: \control,
		current_kind: kind,
		spec: spec,
		selected: 0,	 // bool
		selected_cell: 0,
		bar_length: bar_length,
		default_val: default_value,

		seq: { arg superself;
			(
				//FIXME: handle others "out of range" exceptions
				set_norm_val: { arg self, idx, norm_val;
					superself.seq_val.wrapPut(idx, superself.spec.map(norm_val));
					superself.changed(\val, idx % superself.seq_val.size);
				},
				get_norm_val: { arg self, idx;
					superself.spec.unmap(superself.seq_val[idx]);
				},
				set_val: { arg self, idx, val;
					superself.seq_val[idx] = val;
					superself.changed(\val, idx);
				},
				change: { arg self, fun;
					superself.seq_val = fun.(superself.seq_val);
					superself.changed(\cells);
				}
			);
		},
		seq_val: if(default_value.isArray, { default_value.asList }, { (default_value ! bar_length).asList }),

		scalar: { arg superself;
			(
				set_norm_val: { arg self, norm_val;
					superself.scalar_val = superself.spec.map(norm_val);
					superself.changed(\val, 0);
				},
				set_val: { arg self, idx, val;
					superself.scalar_val = val;
					superself.changed(\val, 0);
				},
				get_norm_val: { arg self;
					superself.spec.unmap(superself.scalar_val);
				}

			);
		},
		scalar_val: if(default_value.isArray, { default_value[0] }, { default_value }),

		select_param: { arg self;
			self.selected = 1;
			self.changed(\selected);
		},
		deselect_param: { arg self;
			self.selected = 0;
			self.changed(\selected);
		},

		select_cell: { arg self, idx;
			var oldsel;
			[idx, self.get_cells].debug("make_control_param.select_cell idx, selg.get_cells");
			if( idx < self.get_cells.size, {
				oldsel = self.selected_cell;
				self.selected_cell = idx;
				self.changed(\selected_cell, oldsel);
			})
		},

		get_selected_cell: { arg self;
			if(self.current_kind == \seq, {
				self.selected_cell;
			}, {
				0
			});
		},

		set_norm_val: { arg self, val;
			if(self.current_kind == \seq, {
				self.seq.set_norm_val( self.selected_cell, val);
			}, {
				self.scalar.set_norm_val(val);
			});
		},

		set_val: { arg self, val;
			if(self.current_kind == \seq, {
				self.seq.set_val( self.selected_cell, val);
			}, {
				self.scalar.set_val(val);
			});
		},

		get_norm_val: { arg self;
			if(self.current_kind == \seq, {
				self.seq.get_norm_val( self.selected_cell);
			}, {
				self.scalar.get_norm_val;
			});
		},

		get_val: { arg self;
			if(self.current_kind == \seq, {
				self.seq_val[self.selected_cell];
			}, {
				self.scalar_val;
			});
		},

		add_cells: { arg self, cells;
			self.seq_val.addAll(cells);
			self.changed(\cells);
		},

		remove_cells: { arg self, num;
			self.seq_val = self.seq_val[.. (self.seq_val.size - num - 1) ];
			self.changed(\cells);
		},

		change_kind: { arg self, kind;
			self.current_kind = kind;
			self.changed(\kind);
		},

		get_cells: { arg self;
			if(self.current_kind == \seq, {
				self.seq_val;
			}, {
				[self.scalar_val]
			});
		},

		refresh: { arg self;
			self.changed(\kind);
			self.changed(\selected);
			self.changed(\cells);
		},

		vpattern: { arg self; 
			Prout({
				var repeat = 1000000;
				var idx, val=0;
				repeat.do {
					switch( self.current_kind,
						\scalar, {
							self.scalar_val.yield;
						},
						\seq, {
							idx = 0;
							val = self.seq_val[idx];
							while( { val.notNil } , { 
								val.yield;
								idx = idx + 1;
								val = self.seq_val[idx];
							});
						}
					);
				}
			})
		}
	);
	ret;
};

~make_player_from_synthdef = { arg defname, data=nil;
	var player;
	var desc = SynthDescLib.global.synthDescs[defname];
	if(desc.isNil, {
		("ERROR: make_player_from_synthdef: SynthDef not found: "++defname).error
	});
	defname.debug("loading player from");
	desc.debug("synthDescs");
	player = (
		bank: 0,
		defname: defname,
		node: EventPatternProxy.new,

		init: { arg self;

			self.data = {
					// use args and defaults values from synthdef to build data dict
					// if data dict given, deep copy it instead
					var dict;
					dict = Dictionary.new;
					if( data.isNil, {
						desc.controls.do({ arg control;
							control.name.debug("making player data name");
							control.defaultValue.debug("making player data");
							control.defaultValue.isArray.debug("making player data");
							dict[control.name.asSymbol] = ~make_control_param.(
								control.name.asSymbol,
								\scalar,
								control.defaultValue,
								~get_spec.(control.name.asSymbol, defname)
							)
						});
					}, {
						dict = data.deepCopy;
					});
					dict;
			}.value;

			self.data[\dur] = self.data[\dur] ?? ~make_control_param.(\dur, \scalar, 0.5, ~get_spec.(\dur, defname));
			self.data[\legato] = self.data[\legato] ?? ~make_control_param.(\legato, \scalar, 0.5, ~get_spec.(\legato, defname));

			self.data[\stepline] = self.data[\stepline] ?? ~make_stepline_param.(\stepline, 1 ! 8 );
			self.data[\instrument] = self.data[\instrument] ?? ~make_literal_param.(\instrument, defname);
			self.data[\type] = ~make_type_param.(\type);

			//TODO: handle t_trig arguments

			self.node.source = {
				var dict = Dictionary.new;
				var list = List[];
				self.data.keys.do { arg argName;
					dict[argName] = self.data[argName].vpattern;
				};
				dict.debug("maked pbind dict");
				dict.pairsDo({ arg key, val; list.add(key); list.add(val)});
				list.debug("maked pbind list");
				//[\type, \stepline, \instrument].do { arg x; list.add(x); list.add(dict[x]) };
				//list.debug("maked pbind list");
				Pbind(*list).dump;
			}.value;
		},

		clone: { arg self;
			~make_player_from_synthdef.(defname, self.data);
		},
		map_arg: { arg self, argName, val;
			argName.debug("mapping hidden!!!");
			~get_spec.(argName, defname).map(val);
		},
		unmap_arg: { arg self, argName, val;
			~get_spec.(argName, defname).unmap(val);
		},

		get_args: { arg self;
			self.data.keys
		},

		set_bank: { arg self, bank;
			self.bank = bank;
			self.data.do { arg x; x.changed(\cells); };
		},

		get_bank: { arg self;
			self.bank;
		},

		get_arg: ~player_get_arg,
		set_arg: ~player_set_arg
	);
	player.init;
	player;
};

~make_player_from_patfun = { arg patfun, data=nil;
	var player;
	player = (
		init: { arg self;

			self.data = {
					// use args and defaults values from synthdef to build data dict
					// if data dict given, deep copy it instead
					var dict;
					dict = Dictionary.new;
					if( data.isNil, {
						patfun.argNames.do({ arg argName, idx;
							dict[argName] = patfun.defaultArgs[idx];
						});
					}, {
						dict = data.deepCopy;
					});
					dict;
			}.value;

			self.node.source = patfun.valueArray( patfun.argNames.collect({ arg argName;
				~make_event_key_reader.(argName, self)
			}));
		},
		patfun: { arg self; patfun; },
		clone: { arg self;
			~make_player_from_patfun.(patfun, self.data);
		},
		map_arg: { arg self, argName, val;
			// TODO: how to get synthdef spec
			~get_spec.(argName).map(val);
		},
		unmap_arg: { arg self, argName, val;
			~get_spec.(argName).unmap(val);
		},
		node: EventPatternProxy.new,
		get_arg: ~player_get_arg,
		set_arg: ~player_set_arg
	);
	player.init;
	player;
};
Spec.add(\dur, ControlSpec(4/128, 4, \lin, 4/64, 0.25, "s"));
Spec.add(\legato, ControlSpec(0, 1.2, \lin, 0, 0.707));
Spec.add(\sustain, \legato);

~make_player = { arg instr, data=nil;
	var player = nil;
	case
		{ instr.isSymbol || instr.isString } {
			player = ~make_player_from_synthdef.(instr.asSymbol, data);
		} 
		{ instr.isFunction } {
			player = ~make_player_from_patfun.(instr, data);
		}
		{ ("ERROR: player type not recognized:"++instr).postln }
	;
	player;
};

// ==========================================
// PARPLAYER FACTORY
// ==========================================

~make_parplayer = { arg plist;
	var pplayer;
	pplayer = (
		init: { arg self;
			self.node.source = Ppar(plist);
		},
		node: EventPatternProxy.new
	);

};

// ==========================================
// SEQUENCER FACTORY
// ==========================================

~mk_sequencer = {(
	model: (
		boardsize: 10 @ 4,
		stepboardsize: 8 @ 4,

		livenodepool: Dictionary.new,
		patpool: Dictionary.new,

		patlib: [ // bank.y.x
			[
				[ \p_snare1, \p_kick1]
			]
		],

		// parlive: ... // parlive matrix


		get_parlive: { arg self, address;
			var ret;
			var path = address.bank @ address.coor.x;
			//address.debug("get_parlive address");
			try {
				ret = self.parlive[ path ][\data][address.coor.y];
				//self.parlive[ path ].debug("get_parlive path contenu");
				if( ret == 0, { ret = "void" });
			} {
				ret = "void";
			};
			//ret.debug("get_parlive ret");
			ret;
		},

		get_pargroup: { arg self, address;
			var path = address.bank @ address.coor.x;
			var ret;
			path.debug("get_pargroup path");
			ret = self.parlive[ path ];
			if( ret.isNil, {
				ret = (
					name: (\group ++ address.coor.x),
					data: [0,0,0,0, 0,0,0,0]
				)
			});
			ret;
		},

		set_parlive: { arg self, address, value;
			var data;
			var path = address.bank @ address.coor.x;
			if( self.parlive.includesKey( path ), {
				self.parlive[ path ][\data][address.coor.y] = value;
			}, {
				data = [0,0,0,0, 0,0,0,0];
				data[address.coor.y] = value;
				self.parlive[ path ] = (
					name: (\group ++ address.coor.x),
					data: data
				);
			})
		},

		del_parlive: { arg self, address;
			self.set_parlive(address, 0);
		},

		parlive: Dictionary[
			0 @ 0 -> (name: \group0, data: [0,0,0,0, 0,0,0,0])
		]
			

	),
	state: (
		// TODO: implement offset
		get_cc: { arg self, ccid;
			// TODO: use whole ccid
			if(ccid.isNil, {
				0.5
			}, {
				self.cc[ccid.number] ?? 0.5;
			});
		},
		set_cc: { arg self, ccid, val;
			// TODO: use whole ccid
			self.cc[ccid.number] = val;
		},
		cc: Dictionary.new,
		selected: (
			coor: 0 @ 0,
			panel: \parlive,
			bank: 0,
			kind: \node // \libnode, \node, \nodegroup
		),
		current: (
			panel: \patlib,
			offset: 0 @ 0,
			bank: 0
		),
		panel: (
			patlib: (
				bank: 0
			),
			editplayer: (
				param: 1,
				step: 3,
				bank: 0
			),
			parlive: (
				bank: 0
			)
		),
		clipboard: (
			node: \p_snare1,
			kind: \node // TODO:
		)
	),

	load_patlib: { arg self, patlist;
		var patpool, patlib, bank = 0, ix = 0, iy = 0;
		patpool = Dictionary.new;

		patlib = ~matrix3_from_list.(patlist, { arg asso;
			patpool[asso.key] = asso.value;
			asso.key;
		});
		self.model.patlib = patlib;
		self.model.patpool = patpool;
	},

	//////////////////////////////////////////////////////////

	archive_livenodepool: { arg self, projpath;
		var dict = Dictionary.new;
		self.model.livenodepool.keysValuesDo { arg key, val;
			(key -> [val.defname, val.data]).writeArchive(projpath++"/livenode_"++key);
		};
	},

	unarchive_livenodepool: { arg self, projpath;
		var path;
		var pool = Dictionary.new;
		path = PathName.new(projpath);
		path.entries.do { arg file;
			var fullname, name, asso;
			fullname = file.fullPath;
			name = file.fileName;
			if(name[..8] == "livenode_", {
				asso = Object.readArchive(fullname);
				pool[asso.key] = ~make_player_from_synthdef.(asso.value[0], asso.value[1]);
			});
		};
		pool;
	},

	save_project: { arg self, name;
		var proj;

		("mkdir "++name).unixCmd;
		proj = ();
		proj.patlib = self.model.patlib;
		proj.patpool = self.model.patpool;
		proj.parlive = self.model.parlive;
		proj.state = self.state;

		self.archive_livenodepool(name);
		
		proj.writeArchive(name++"/core");

	},
	
	load_project: { arg self, name;
		var proj;
		proj = Object.readArchive(name++"/core");
		if(proj.notNil, {
			self.model.patlib = proj.patlib.debug("patlib=============================");
			self.model.patpool = proj.patpool.debug("patpool===============================");
			self.model.livenodepool = self.unarchive_livenodepool(name).debug("livenodepool==================");
			self.state = proj.state.debug("state====================================");
			self.model.parlive = proj.parlive.debug("parlive=================================");
			//self.model = pro[0];
			//self.state = pro[1];
		}, {
			("Project `"++name++"' can't be loaded").postln
		});
	},

	///////////////////////////////////////////////////////:::


	make_livenodename_from_libnodename: { arg self, name;
		// TODO: handle name collision
		name++"_l"++UniqueID.next;

	},

	make_newlivenodename_from_livenodename: { arg self, name;
		// TODO: make it real
		name[ .. name.findBackwards("_l")  ] ++ "l" ++ UniqueID.next;
	},

	make_livenode_from_libnode: { arg self, libnodename;
		var livenodename;
		livenodename = self.make_livenodename_from_libnodename(libnodename);
		self.model.livenodepool[livenodename] = ~make_player.(self.model.patpool[libnodename]);
		livenodename;
	},

	duplicate_livenode: { arg self, livenodename;
		var newlivenodename, newlivenode, newlivenode_pdict;
		newlivenodename = self.make_newlivenodename_from_livenodename(livenodename);
		newlivenodename.debug("newlivenodename");
		livenodename.debug("livenodename");
		self.model.livenodepool.debug("livenodepool");
		self.model.livenodepool[newlivenodename] = self.model.livenodepool[livenodename].clone;
		newlivenodename;
	},

	///////////////////////////////////////////////////////////////////////////////////////
	//////////////////// HANDLERS
	///////////////////////////////////////////////////////////////////////////////////////

	shift_address: { arg self, ad;
		var address = ad.deepCopy;
		address.coor.y = address.coor.y-1;
		address;
	},

	copy_selection: { arg self;
		var sel = self.state.selected;
		switch( sel.panel,
			\parlive, {
				self.state.clipboard.node = self.model.get_parlive(self.shift_address(sel));
				self.state.clipboard.kind = sel.kind;
			},
			\patlib, {
				self.state.clipboard.node = self.model.patlib[sel.bank][sel.coor.x][sel.coor.y];
				self.state.clipboard.kind = sel.kind;
			}
		);
	},

	remove_selection: { arg self;
		var sel = self.state.selected;
		switch( sel.panel,
			\parlive, {
				switch(sel.kind,
					\node, {
						self.model.set_parlive(sel, 0);
						self.refresh_parlive_button(sel);
					},
					\nodegroup, {
						"NOT_IMPLEMENTED".debug("remove nodegroup");
					}
				);
			},
			\patlib, {
				"FORBIDEN".debug("cut in libnode");
			}
		);


	},

	handlers: { arg self, input;
		input.debug("=====#############====== EVENT");
		switch( input[0],
			\select_libnode, {
				var libnode_name;
				var livenode_name;
				var source_coor = input[1];
				var source_bank = self.state.current.bank;
				var target_sel = self.state.selected.deepCopy;
				var sel = self.state.selected;

				self.state.selected.debug("select_libnode selected");

				libnode_name = self.model.patlib[source_bank][source_coor.x][source_coor.y];
				livenode_name = self.make_livenode_from_libnode(libnode_name);

				livenode_name.debug("new livenode name");
				self.model.livenodepool.debug("livenodepool");
				self.model.livenodepool[livenode_name].data.debug("select_libnode liveplayer.data");

				self.model.set_parlive(target_sel, livenode_name);

				self.model.debug("MODEL");

				// back to parlive panel

				self.state.current.panel = \parlive;
				self.state.current.bank = self.state.panel[\parlive].bank;
				self.refresh_current_panel;
			},
			\select, {
				// select livenode
				var coor = input[1];
				var oldsel = self.state.selected.deepCopy;

				self.state.selected.coor = coor;
				self.state.selected.panel = self.state.current.panel;
				self.state.selected.bank = self.state.current.bank;
				self.state.selected.kind = if( coor.y < 0, { \nodegroup.debug("====selected group!!!"); },{ \node } );

				//self.debug("bah quoi");
				self.state.selected.debug("selected");

				// deselect old button
				self.refresh_parlive_button(oldsel);
				// select new button
				self.refresh_parlive_button(self.state.selected);
			},
			\delete, {

				self.model.del_parlive(self.state.selected);
				self.refresh_parlive_button(self.state.selected);

			},
			\copy, {
				self.copy_selection;
			},
			\cut, {
				var sel = self.state.selected;
				self.copy_selection;
				self.remove_selection;
			},
			\paste, {
				var sel = self.state.selected;
				// TODO: modify name when pasting from patlib to parlive
				sel.debug("PASTE TO");
				self.state.clipboard.debug("PASTE FROM");
				switch( self.state.selected.kind, // TO
					\libnode, {
						"FORBIDEN".debug("paste to libnode");
					},
					\node, {
						switch( self.state.clipboard.kind, // FROM
							\node, {
								// copy clipboard livenode and overwrite selected livenode
								// TODO: implement trashcan

								var name;
								var sel = self.state.selected;
								var address = self.state.selected.deepCopy;
								address.coor.y = address.coor.y-1;
								name = self.duplicate_livenode(self.state.clipboard.node);
								name.debug("new node name");

								self.model.set_parlive(address, name);
								self.refresh_parlive_button(sel);
								self.model.debug("MODEL");
							},
							\nodegroup, {
								// link nodegroup as livenode

							}
						);
					},
					\nodegroup, {
						switch( self.state.clipboard.kind,
							\libnode, {
								"FORBIDEN".debug("paste to libnode");
							},
							\node, {
								// append livenode to nodegroup
							},
							\nodegroup, {
								// copy clipboard nodegroup and overwrite selected nodegroup
								// TODO: implement trashcan
							}
						);
					}
				);
			},
			\change_panel, {
				var newpan = input[1];
				if( (newpan == \patlib)
					&& (self.state.selected.kind == \nodegroup), {
					"cannot load libnode to groupnode".debug("FORBIDEN");
				}, { 
					self.state.current.panel = newpan;
					self.state.panel.debug("change_panel state.panel");
					self.state.current.bank = self.state.panel[newpan].bank;
					self.refresh_current_panel;
				});
			},
			\change_bank, {
				var curpan = self.state.current.panel;
				"2".debug("FAUSSE");
				self.state.current.bank = input[1];
				"3".debug("FAUSSE");
				self.state.panel[curpan].bank = self.state.current.bank;
				"4".debug("FAUSSE");
				self.refresh_current_panel;
				"5".debug("FAUSSE");
			},

			\play_selected, {
				var player;
				var group;
				self.state.selected.debug("play_selected sel");
				if( self.state.selected.kind == \nodegroup, {
					group = self.model.get_pargroup(self.state.selected);
					group.data.do { arg name;
						if(name != 0, {
							self.model.livenodepool[name].node.play;
						});
					};
				}, {
					player = self.get_selected_player;
					if( player.isNil, { 
						"Dont play void player".debug("NONO");
					}, {
						
						player.node.dump;	
						player.node.source.dump;	
						player.node.play;	
						"fin play".debug;

					});
				});
			},
			\stop_selected, {
				var player;
				var group;
				if( self.state.selected.kind == \nodegroup, {
					group = self.model.get_pargroup(self.state.selected);
					group.data.do { arg name;
						if(name != 0, {
							self.model.livenodepool[name].node.stop;
						});
					};
				}, {
					player = self.get_selected_player;
					if( player.isNil, { 
						"Dont stop void player".debug("NONO");
					}, {
						player.node.stop;
					});
				});
			}
		)
	},


	make_kb_handlers: { arg self;

		self.kb_handler = Dictionary.new;

		// Fx functions

		self.kb_handler[ [~modifiers.fx, ~kbfx[0]] ] = { self.handlers( [\copy] ) };
		self.kb_handler[ [~modifiers.fx, ~kbfx[1]] ] = { self.handlers( [\cut] ) };
		self.kb_handler[ [~modifiers.fx, ~kbfx[3]] ] = { self.handlers( [\paste] ) };

		self.kb_handler[ [~modifiers.fx, ~kbfx[4]] ] = { self.handlers( [\play_selected] ) };
		self.kb_handler[ [~modifiers.fx, ~kbfx[5]] ] = { self.handlers( [\stop_selected] ) };

		self.kb_handler[ [~modifiers.fx, ~kbfx[8]] ] = { self.handlers( [\change_panel, \parlive] ) };
		self.kb_handler[ [~modifiers.fx, ~kbfx[9]] ] = { self.handlers( [\change_panel, \patlib] ) };
		self.kb_handler[ [~modifiers.fx, ~kbfx[11]] ] = { self.handlers( [\change_panel, \editplayer] ) };

		// quant

		self.kb_handler[ [~modifiers.ctrl, ~kbcalphanum["q"]] ] = { 
			
			~kbnumpad.do { arg keycode, idx;
				self.kb_handler[[0, keycode]] = { 
					EventPatternProxy.defaultQuant = idx;

					// restore bank change shortcuts
					// FIXME: when some other shortcults are set to kbnumpad
					~kbnumpad.do { arg keycode, idx;
						self.kb_handler[[0, keycode]] = { self.handlers( [\change_bank, idx] ) };
					};

					("=== EventPatternProxy.defaultQuant changed to: " ++ EventPatternProxy.defaultQuant).postln;
				};
			};
			
		};



		self.window.view.keyDownAction = { arg view, char, modifiers, u, k; 
			u.debug("ooooooooooooo u");
			modifiers.debug("ooooooooooooo modifiers");
			self.kb_handler[[modifiers,u]].value
		};


	},


	make_parlive_handlers: { arg self;

		~kbpad8x4.do { arg line, iy;
			line.do { arg key, ix;
				// data_coor
				self.kb_handler[[0, key]] = { self.handlers( [\select, ix @ iy] ) };
			}
		};

		~kbpad8x4[0].do { arg key, ix;
			// data_coor
			self.kb_handler[[~modifiers.alt, key]] = { self.handlers( [\select, ix @ (-1)] ) };
		};

		~kbnumpad.do { arg keycode, idx;
			self.kb_handler[[0, keycode]] = { self.handlers( [\change_bank, idx] ) };
		};

		self.kb_handler[[0, ~kbspecial.delete]] = { self.handlers( [\delete] ) };

	},

	make_patlib_handlers: { arg self;

		~kbpad8x4.do { arg line, iy;
			line.do { arg key, ix;
				self.kb_handler[[0, key]] = { self.handlers( [\select_libnode, ix @ iy] ) };
			}
		};

		~kbnumpad.do { arg keycode, idx;
			self.kb_handler[[0, keycode]] = { self.handlers( [\change_bank, idx] ) };
		};

	},
	
	make_editplayer_handlers: { arg self;


	},

	///////////////////////////////////////////////////////////////////////////////////////
	//////////////////// GUI
	///////////////////////////////////////////////////////////////////////////////////////

	width: 1310,
	height: 800,

	make_cell_button: { arg self, parent, label, action;
		var bt;

		bt = GUI.button.new(parent, Rect(50,50,50,50));
		bt.states = [
			[ "  " ++ label ++ " ", Color.black, Color.white],
			[ "> " ++ label ++ " ", Color.black, Color.white],
			[ "  " ++ label ++ " ", Color.white, Color.black ],
			[ "> " ++ label ++ " ", Color.white, Color.black ],
		];
		bt.value = 0;

		bt.action = action
	},

	get_player_state: { arg self, address;
		var sel = self.state.selected, state = Dictionary.new;
		// selection
		if( (address.panel == sel.panel)
			&& (address.bank == sel.bank)
			&& (address.coor == sel.coor), {
			state[\selection] = \selected;
		}, {
			state[\selection] = \deselected;
		});
		// playing
		if( address.panel == \patlib, {
			state[\playing] = 	\stop;
		}, {
			try {
				if( self.livenodepool[ self.get_selected_name ].node.isPlaying, {
					state[\playing] = \play;
				}, {
					state[\playing] = \stop;
				});
			} {
				state[\playing] = \stop;
			};
		});
		//sel.debug("get_player_state sel");
		//address.debug("get_player_state address");
		//state.debug("get_player_state end state");
		state;
	},

	refresh_parlive_button: { arg self, address;
		var button, label, state;
		var sel = self.state.selected;
		button = self.window.view.children[address.coor.x].children[address.coor.y+1];
		if( address.coor.y < 0, {
			// nodegroup
			self.model.get_pargroup(address).name;
			button.states = [
				[ "  " ++ label ++ " ", Color.black, Color.white],
				[ "  " ++ label ++ " ", Color.white, Color.black ],
			];
			button.value = if( (address.panel == sel.panel)
					&& (address.bank == sel.bank)
					&& (address.coor.y == sel.coor.y)
					&& (address.coor.x == sel.coor.x), {
				1
			}, {
				0
			});
		}, {
			// livenode
			label = self.model.get_parlive(address);
			button.states = [
				[ "  " ++ label ++ " ", Color.black, Color.white],
				[ "> " ++ label ++ " ", Color.black, Color.white],
				[ "  " ++ label ++ " ", Color.white, Color.black ],
				[ "> " ++ label ++ " ", Color.white, Color.black ],
			];
			state = self.get_player_state(address);
			button.value = if( state[\selection] == \deselected, {
				if( state[\playing] == \stop, {
					0;
				}, {
					1;
				});
			}, {
				if( state[\playing] == \stop, {
					2;
				}, {
					3;
				});
			});
		});
	},

	player_states: (
		\selection: [\selected, \deselected],
		\playing: [\stop, \play, \pause, \prepare_play, \prepare_stop, \prepare_resume],
		\clipboard: [\copy, \cut]
	),

	get_selected_name: { arg self;
		var sel = self.state.selected;
		var address = self.state.selected.deepCopy;
		var ret;
		//address.debug("get_selected_name address");
		//sel.debug("get_selected_name sel");
		switch( sel.panel,
			\parlive, {
				//address.debug("address");
				ret = self.model.get_parlive(address);
				//ret.debug("arf ret");
				//"hein".debug("bah oui");
			},
			\patlib, {
				//"gneeee".debug("bah oui");
				ret = self.model[sel.panel][sel.bank][sel.coor.x][sel.coor.y];
			}
		);
		//"quoi".debug("bah oui");
		//ret.debug("bah ret");
		ret;
	},

	get_selected_player: { arg self;
		var name = self.get_selected_name;
		name.debug("get_selected_player name");
		self.model.livenodepool[name];
	},

	make_parlive_view: { arg self; 
	
		var ps_col_layout, curbank, data_address;
		var parent = self.window;
		curbank = self.state.current.bank;

		8.do { arg rx;
			var label;

			ps_col_layout = GUI.vLayoutView.new(parent, Rect(0,0,(self.width+10)/9,60*8));
			ps_col_layout.background = Color.rand;

			label = self.model.get_pargroup((bank: curbank, coor: rx @ (-1)))[\name];

			self.make_cell_button(ps_col_layout, label, {  });
			data_address = (
				coor: rx @ (-1),
				bank: curbank,
				panel: \parlive
			);
			self.refresh_parlive_button(data_address);

			8.do { arg ry;

				//ry.debug("maih QUOIIIII");
				label = self.model.get_parlive((coor: rx @ ry, bank: curbank));
				self.make_cell_button(ps_col_layout, label, {  });
				data_address = (
					coor: rx @ ry,
					bank: curbank,
					panel: \parlive
				);
				self.refresh_parlive_button(data_address);
			};

		};


	},

	make_patlib_view: { arg self;

		var ps_col_layout, curbank, address;
		var parent = self.window;
		curbank = self.state.current.bank;
		"BEGIN".debug("make_patlib_view");

		self.model.patlib[curbank].do { arg col, rx;
			ps_col_layout = GUI.vLayoutView.new(parent, Rect(0,0,(self.width+10)/9,60*6));
			ps_col_layout.background = Color.rand;

			col.do { arg cell, ry;
				var label;
				label = cell;
				self.make_cell_button(ps_col_layout, label, {  });
				address = (
					coor: rx @ ry,
					bank: curbank,
					panel: \patlib
				);
			};

		};

	},

	clear_current_panel: { arg self;
		self.window.view.removeAll.value;
		self.window.view.decorator = FlowLayout(self.window.view.bounds); // notice that FlowView refers to w.view, not w
		self.clear_kb_handler.value;
	},

	refresh_current_panel: { arg self;
		switch(self.state.current.panel,
			\parlive, { self.show_parlive_panel },
			\patlib, { self.show_patlib_panel },
			\editplayer, { self.show_editplayer_panel }
		);

	},

	show_parlive_panel: { arg self;
		self.clear_current_panel.value;
		self.state.current.panel = \parlive;
		self.make_parlive_view;
		self.make_parlive_handlers;
		self.window.view.focus(true);
	},

	show_patlib_panel: { arg self;
		self.clear_current_panel.value;
		self.state.current.panel = \patlib;
		self.make_patlib_view;
		self.make_patlib_handlers;
		self.window.view.focus(true);
	},

	show_editplayer_panel: { arg self;
		var sel;
		sel = self.get_selected_player.();
		if( sel.notNil, {
			self.clear_current_panel.value;
			self.state.current.panel = \editplayer;
			~make_editplayer.(sel, self.window, self.kb_handler);
			self.make_editplayer_handlers;
			self.window.view.focus(true);
		});
	},

	make_window: { arg self;
		var window, buttons, handlers;
		var ul = [];
		window = GUI.window.new("seq", Rect(50, 50, self.width, self.height)).front;
		window.view.decorator = FlowLayout(window.view.bounds); // notice that FlowView refers to w.view, not w

		window;

	},

	make_gui: { arg self;
		self.window = self.make_window.value;
		self.make_kb_handlers;
		self.show_parlive_panel;

	}


)};
)
