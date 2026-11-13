import copy
import importlib.util
import json
import os
from pathlib import Path
import tempfile
import threading
import unittest
from datetime import datetime, timezone, timedelta
from http.client import HTTPConnection
from unittest.mock import patch
from uuid import uuid4

IMPORT_DIR = tempfile.TemporaryDirectory()
os.environ['COMPANION_DATA'] = IMPORT_DIR.name
spec = importlib.util.spec_from_file_location('companion_server', Path(__file__).with_name('server.py'))
s = importlib.util.module_from_spec(spec)
spec.loader.exec_module(s)


class CompanionTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        base = Path(self.tmp.name)
        for name, value in [('BASE', base), ('DB', base/'inbox.sqlite'), ('KEY_FILE', base/'snapshot.key'), ('TOKEN_FILE', base/'token')]:
            p = patch.object(s, name, value)
            p.start()
            self.addCleanup(p.stop)
        s.TOKEN_FILE.write_text('test-token-not-a-real-secret')
        s.init()
        self.state = dict(enabled=True, conversation_id=str(uuid4()), assistant_id=str(uuid4()), persona='test persona', memory='test memory', context='test context', morning=True, proactive=True, busy=False, last_user_at=0, provider=dict(type='openai', url='https://example.invalid/v1/chat/completions', key='test-key-not-a-real-secret', model='test-model', headers={}))
        self.now = datetime(2026, 11, 13, 12, 0, tzinfo=s.TZ)
        self.server = s.ThreadingHTTPServer(('127.0.0.1', 0), s.Handler)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.addCleanup(self.stop_server)

    def stop_server(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join()

    def request(self, path, body=None, auth=True):
        client = HTTPConnection('127.0.0.1', self.server.server_port, timeout=3)
        headers = {'Authorization': 'Bearer test-token-not-a-real-secret'} if auth else {}
        client.request('POST' if body is not None else 'GET', '/companion/'+path, json.dumps(body) if body is not None else None, headers)
        r = client.getresponse()
        result = r.status, json.loads(r.read())
        client.close()
        return result

    def save(self, state=None):
        with patch.object(s.time, 'time', return_value=self.now.timestamp()):
            return s.save_snapshot(state or self.state)

    def tick(self, text='hello', now=None, side_effect=None):
        now = now or self.now
        class Clock(datetime):
            @classmethod
            def now(cls, tz=None): return now.astimezone(tz or s.TZ)
        with patch.object(s, 'datetime', Clock) as clock, patch.object(s.time, 'time', return_value=now.timestamp()), patch.object(s, 'generate', return_value=text, side_effect=side_effect) as generate, patch.object(s, 'weather', side_effect=OSError('offline')):
            result = s.tick(force=True)
            return result, generate

    def insert(self, slot='test-slot', kind='heartbeat', created=None):
        key = str(uuid4())
        with s.db() as c:
            c.execute('INSERT INTO inbox VALUES(?,?,?,?,?,?,?,0)', (key, self.state['conversation_id'], self.state['assistant_id'], 'test message', created or self.now.timestamp(), kind, slot))
        return key

    def test_unauthorized(self):
        for path, body in [('messages', None), ('sync', self.state), ('ack', {'ids': []})]:
            self.assertEqual(401, self.request(path, body, False)[0])

    def test_sync_messages_ack(self):
        self.assertEqual(200, self.request('sync', self.state)[0])
        key = self.insert()
        self.assertEqual(key, self.request('messages')[1]['messages'][0]['id'])
        self.assertEqual(200, self.request('ack', {'ids': [key]})[0])
        self.assertEqual([], self.request('messages')[1]['messages'])
        self.assertEqual(200, self.request('ack', {'ids': [key]})[0])

    def test_same_slot_unique(self):
        self.save()
        self.assertEqual('queued', self.tick()[0]['status'])
        with s.db() as c:
            c.execute('UPDATE inbox SET acked=1, created=created-20000')
        self.assertEqual('duplicate', self.tick()[0]['status'])
        with s.db() as c:
            self.assertEqual(1, c.execute('SELECT COUNT(*) FROM inbox').fetchone()[0])

    def test_disabled_does_not_generate(self):
        self.save({'enabled': False})
        result, generate = self.tick()
        self.assertEqual('not_due', result['status'])
        generate.assert_not_called()

    def test_changed_revision_rejected(self):
        self.save()
        def changed(*args):
            state = copy.deepcopy(self.state)
            state['memory'] = 'new authoritative memory'
            s.save_snapshot(state)
            return 'old response'
        self.assertEqual('context_changed', self.tick(side_effect=changed)[0]['status'])
        self.assertEqual([], self.request('messages')[1]['messages'])

    def test_disable_during_generation(self):
        self.save()
        def disable(*args):
            s.save_snapshot({'enabled': False})
            return 'must not deliver'
        self.assertEqual('context_changed', self.tick(side_effect=disable)[0]['status'])

    def test_weather_failure_explicit(self):
        self.now = self.now.replace(hour=8)
        self.save()
        result, generate = self.tick()
        self.assertEqual('queued', result['status'])
        self.assertIn('禁止编造天气或温度', generate.call_args.args[1])

    def test_weekdays_only(self):
        for day in range(9, 16):
            now = self.now.replace(day=day, hour=8)
            self.assertEqual(now.weekday()<5, s.morning_due(self.state, now, False))
        self.assertFalse(s.morning_due(self.state, self.now.replace(hour=8), True))

    def test_morning_once(self):
        self.now = self.now.replace(hour=8)
        self.save()
        self.assertEqual('queued', self.tick()[0]['status'])
        result, generate = self.tick()
        self.assertEqual('not_due', result['status'])
        generate.assert_not_called()

    def test_quiet_hours(self):
        for hour in [0, 7, 23]:
            self.assertFalse(s.awake(self.now.replace(hour=hour)))
        self.assertFalse(s.awake(self.now.replace(day=14, hour=10)))
        self.assertTrue(s.awake(self.now.replace(day=14, hour=11)))

    def test_followup_then_wait(self):
        self.save()
        self.insert(created=self.now.timestamp()-3600)
        self.assertEqual('follow_up', self.tick()[0]['kind'])
        result, generate = self.tick(now=self.now+timedelta(minutes=20))
        self.assertEqual('unread_wait', result['status'])
        generate.assert_not_called()

    def test_short_and_long_gaps(self):
        import random
        gaps = [s.sample_gap(random.Random(i)) for i in range(1000)]
        self.assertLess(min(gaps), 15*60)
        self.assertGreater(max(gaps), 14*3600)
        late = self.now.replace(hour=22, minute=50)
        target = datetime.fromtimestamp(s.advance(late.timestamp(), 30*60), s.TZ)
        self.assertEqual((14,11,20), (target.day,target.hour,target.minute))

    def test_other_conversation_does_not_block(self):
        self.save()
        self.insert(slot='old1'); self.insert(slot='old2')
        other = copy.deepcopy(self.state)
        other['conversation_id'] = str(uuid4())
        self.save(other)
        self.assertEqual('queued',self.tick()[0]['status'])

    def test_no_three_per_day_limit(self):
        self.state['last_user_at'] = self.now.timestamp()-1800
        self.save()
        for n in range(10):
            self.insert(slot=f'past-{n}',created=self.now.timestamp()-3600-n)
        with s.db() as c: c.execute('UPDATE inbox SET acked=1')
        self.assertEqual('queued',self.tick()[0]['status'])

    def test_reschedule_persisted(self):
        self.save()
        self.assertEqual('queued',self.tick()[0]['status'])
        with s.db() as c:
            self.assertGreater(s.meta_get(c,'hb_next'),self.now.timestamp())

    def test_skip_not_queued(self):
        self.save()
        self.assertEqual('quiet', self.tick('[SKIP]')[0]['status'])
        self.assertEqual([], self.request('messages')[1]['messages'])

    def test_snapshot_encrypted(self):
        self.save()
        with s.db() as c:
            raw = c.execute('SELECT body FROM state').fetchone()[0]
        self.assertNotIn('test-key-not-a-real-secret', raw)
        self.assertNotIn('test persona', raw)
        self.assertEqual('test memory', s.snapshot()[0]['memory'])
        self.assertEqual(0o600, s.KEY_FILE.stat().st_mode & 0o777)
        self.assertEqual(0o600, s.DB.stat().st_mode & 0o777)

    def test_disable_and_rebind_clear_old_queue(self):
        self.save()
        self.insert()
        self.assertEqual(200, self.request('sync', {'enabled': False})[0])
        self.assertEqual([], self.request('messages')[1]['messages'])
        self.save()
        self.assertEqual([], self.request('messages')[1]['messages'])
        self.insert(slot='another')
        state = copy.deepcopy(self.state)
        state['conversation_id'] = str(uuid4())
        self.save(state)
        self.save()
        self.assertEqual([], self.request('messages')[1]['messages'])

    def test_invalid_payload_rejected(self):
        for body in [[], {}, {'enabled': 'true'}, {'enabled': True}, {'enabled': True, 'provider': []}]:
            self.assertEqual(400, self.request('sync', body)[0])

    def test_identical_sync_keeps_revision(self):
        self.assertEqual(self.save(), self.save())

    def test_stale_busy_and_recent_user(self):
        t = self.now.timestamp()
        self.assertFalse(s.usable(self.state, t-49*3600, self.now))
        self.state['busy'] = True
        self.assertFalse(s.usable(self.state, t, self.now))
        self.state['busy'] = False
        self.state['last_user_at'] = t-60
        self.assertFalse(s.usable(self.state, t, self.now))

    def test_provider_streams(self):
        class Response:
            def __init__(self, events): self.events=events
            def __enter__(self): return self
            def __exit__(self,*args): pass
            def __iter__(self):
                for event in self.events:
                    yield ('data: '+json.dumps(event)+chr(10)).encode()
        cases = {
            'openai': [{'choices':[{'delta':{'content':'你好'},'finish_reason':None}]}, {'choices':[{'delta':{},'finish_reason':'stop'}]}],
            'claude': [{'type':'content_block_delta','delta':{'type':'text_delta','text':'你好'}},{'type':'message_stop'}],
            'responses': [{'type':'response.output_text.delta','delta':'你好'},{'type':'response.completed'}],
        }
        for kind, events in cases.items():
            state=copy.deepcopy(self.state); state['provider']['type']=kind
            with patch.object(s,'build_opener') as opener:
                opener.return_value.open.return_value=Response(events)
                self.assertEqual('你好',s.generate(state,'test'))
                sent=json.loads(opener.return_value.open.call_args.args[0].data)
                if kind=='responses': self.assertFalse(sent['store'])
                opener.return_value.open.return_value=Response(events[:1])
                with self.assertRaises(RuntimeError): s.generate(state,'test')

    def test_invalid_activity(self):
        for value in [True, -1, float('nan'), float('inf')]:
            state=copy.deepcopy(self.state); state['last_user_at']=value
            self.assertEqual(400,self.request('sync',state)[0])

    def test_quiet_reschedules_without_forcing(self):
        self.save()
        class Clock(datetime):
            @classmethod
            def now(cls,tz=None): return datetime(2026,11,13,12,0,tzinfo=s.TZ)
        with patch.object(s,'datetime',Clock), patch.object(s,'generate',return_value='[SKIP]') as generate:
            s.reschedule(self.now.timestamp()-3600,gap=1)
            self.assertEqual('quiet',s.tick()['status'])
            self.assertEqual('not_due',s.tick()['status'])
            self.assertEqual(1,generate.call_count)


if __name__ == '__main__':
    unittest.main()
